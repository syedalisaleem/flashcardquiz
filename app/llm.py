"""LLM client with JSON-mode extraction, refill loops, response caching,
429 rate-limit auto-pause, and mock mode.

Generation functions are generators that yield batches of validated cards,
so a background job can persist + report progress as batches arrive.
"""

from __future__ import annotations

import hashlib
import json
import logging
import time
from typing import Callable, Iterator, Optional, TypeVar

from openai import OpenAI, OpenAIError

from . import db
from .config import get_settings
from .prompts import FLASHCARD_SYSTEM, MCQ_SYSTEM
from .schemas import Flashcard, MCQ

T = TypeVar("T")

logger = logging.getLogger(__name__)

PROMPT_VERSION = "v2"

RETRY_PAD_SECONDS = 5.0   # extra wait after the reset timestamp
MAX_PAUSE_SECONDS = 30 * 3600  # don't wait longer than ~a day; daily resets fit
# Provider statuses that mean "later, not never" (pollinations quota = 402).
TRANSIENT_STATUSES = (402, 429, 500, 502, 503, 504)
# Cap on backoff retries when the provider gives no reset timestamp:
# 5+10+20+40s then 11x60s ≈ 12 minutes before the job gives up.
MAX_BACKOFF_RETRIES = 15
# Cap on retries when the model answers 200 OK but with truncated/garbled
# JSON (pollinations cuts strings mid-output): 5+10+20+40s ≈ 75s, then fail.
MAX_BAD_JSON_RETRIES = 4
# Explicit output cap; without it the provider may stop mid-string.
MAX_TOKENS = 4096
# pollinations' free endpoint hard-caps completions at 1500 tokens
# (finish_reason=length, our max_tokens is ignored), so each LLM call must
# ask for a small, explicitly-counted batch that fits comfortably under it.
MAX_CARDS_PER_CALL = 8
MAX_MCQS_PER_CALL = 2
# Refill attempts per source chunk (bounded output means more calls).
MAX_REFILLS_PER_CHUNK = 12

OnPause = Optional[Callable[[Optional[int], float], None]]  # (reset_ms, wait_s) ; 0/0 = resumed


class RateLimitError(RuntimeError):
    """Free-tier quota exhausted; `reset_ts_ms` is when the daily window resets."""

    def __init__(self, reset_ts_ms: Optional[int] = None):
        super().__init__("Rate limit exceeded (free-models-per-day)")
        self.reset_ts_ms = reset_ts_ms


MOCK_FLASHCARDS = [
    {
        "type": "Basic",
        "front": "Mitochondria: Primary energy molecule produced",
        "back": "ATP",
        "tags": ["mock", "Biology"],
        "source": "Slide 2",
    },
    {
        "type": "Basic",
        "front": "DNA replication: Enzyme that unwinds the double helix",
        "back": "DNA Helicase",
        "tags": ["mock", "Biology"],
        "source": "Slide 5",
    },
    {
        "type": "Cloze",
        "text": "The {{c1::mitochondria}} is responsible for producing {{c2::ATP}}.",
        "tags": ["mock", "Biology"],
        "source": "Slide 2",
    },
    {
        "type": "Cloze",
        "text": "{{c1::RNA Primase}} lays down RNA primers to initiate DNA synthesis.",
        "tags": ["mock", "Biology"],
        "source": "Slide 5",
    },
    {
        "type": "Basic",
        "front": "Cell Biology: Site of protein synthesis in the cell",
        "back": "Ribosome",
        "tags": ["mock", "Biology"],
        "source": "Slide 3",
    },
]

MOCK_MCQS = [
    {
        "question": "Which enzyme is primarily responsible for unwinding the DNA double helix during replication?",
        "options": ["DNA Helicase", "DNA Polymerase", "RNA Primase", "DNA Ligase"],
        "correct_index": 0,
        "explanation": "Helicase breaks the hydrogen bonds between strands, separating the double helix.",
        "distractor_explanations": {
            "1": "Incorrect. Polymerase synthesizes the new strand, but does not unwind the double helix.",
            "2": "Incorrect. Primase lays down RNA primers to initiate synthesis.",
            "3": "Incorrect. Ligase seals nicks in the sugar-phosphate backbone.",
        },
        "tags": ["mock", "Biology"],
    },
    {
        "question": "Which organelle produces the cell's ATP?",
        "options": ["Mitochondria", "Ribosome", "Golgi apparatus", "Nucleus"],
        "correct_index": 0,
        "explanation": "The mitochondria run cellular respiration, producing ATP.",
        "distractor_explanations": {
            "1": "Incorrect. Ribosomes synthesize proteins.",
            "2": "Incorrect. The Golgi apparatus modifies and packages proteins.",
            "3": "Incorrect. The nucleus stores genetic material.",
        },
        "tags": ["mock", "Biology"],
    },
]


def _client() -> OpenAI:
    s = get_settings()
    return OpenAI(api_key=s.openai_api_key, base_url=s.openai_base_url)


def _extract_json(text: str, model: type[T]) -> list[T]:
    """Parse a JSON array (or single object / wrapped object) into typed models."""
    text = text.strip()
    if text.startswith("```"):
        text = text.strip("`")
        if text.startswith("json"):
            text = text[4:]
    if not text.startswith("["):
        obj = json.loads(text)
        if isinstance(obj, dict):
            for key in ("flashcards", "mcqs", "cards", "data", "items", "questions"):
                if key in obj:
                    text = json.dumps(obj[key])
                    break
            else:
                # single card/question object (e.g. {"type": "Basic", ...})
                text = f"[{text}]"
    data = json.loads(text)
    if not isinstance(data, list):
        raise ValueError("Expected a JSON array from the model")
    items: list[T] = []
    for item in data:
        try:
            items.append(model.model_validate(item))
        except Exception:
            continue  # skip malformed items instead of failing the whole batch
    return items


def _cache_key(system: str, user: str) -> str:
    s = get_settings()
    raw = f"{PROMPT_VERSION}|{s.llm_model}|{s.llm_temperature}|{system}\n{user}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


_GEMINI_HOST = "generativelanguage.googleapis.com"
_GEMINI_FALLBACKS = ("gemini-3.5-flash-lite", "gemini-3.6-flash")


def _chat_models(s) -> list[str]:
    """Primary model first; on the Gemini OpenAI-compat endpoint the sibling
    models ride through a single model's 429/503 outage (they are often
    healthy while the primary is rate-limited or unavailable)."""
    primary = (s.llm_model or "").strip() or "gpt-4o-mini"
    models = [primary]
    if _GEMINI_HOST in (s.openai_base_url or ""):
        for m in _GEMINI_FALLBACKS:
            if m not in models:
                models.append(m)
    return models


def _chat(system: str, user: str) -> str:
    """Raw chat call with JSON-mode enabled; falls back to a plain call when
    the provider rejects response_format or answers it with an empty message
    (pollinations intermittently does either).

    Transient failures (429/5xx) and empty completions walk the fallback
    model list before the caller pauses, so one dead model does not stall
    generation while its siblings are healthy."""
    s = get_settings()
    kwargs = dict(
        temperature=s.llm_temperature,
        max_tokens=MAX_TOKENS,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ],
    )
    client = _client()
    last_transient: Optional[RateLimitError] = None

    for model_name in _chat_models(s):

        def attempt(json_mode: bool, _model: str = model_name) -> str:
            kw = dict(kwargs)
            kw["model"] = _model
            if json_mode:
                kw["response_format"] = {"type": "json_object"}
            resp = client.chat.completions.create(**kw)
            if not resp.choices:
                return ""
            choice = resp.choices[0]
            if choice.finish_reason == "length":
                logger.warning(
                    "LLM output cut by the provider's ~1500-token cap "
                    "(finish_reason=length, %d chars) - batch size too big?",
                    len(choice.message.content or ""),
                )
            return (choice.message.content or "").strip()

        content = ""
        try:
            try:
                content = attempt(True)
            except OpenAIError as exc:
                _raise_rate_limit(exc)
                # response_format rejected: fall through to plain mode
            if not content:
                try:
                    content = attempt(False)
                except OpenAIError as exc:
                    _raise_rate_limit(exc)
                    raise
        except RateLimitError as rle:
            last_transient = rle
            continue
        if content:
            return content
        # Empty completion (reasoning-only output, or a cache miss upstream):
        # treat as transient so the caller backs off - but first try the
        # next model instead of stalling on a model that keeps answering empty.
        last_transient = RateLimitError(None)

    if last_transient is not None:
        raise last_transient
    raise RateLimitError(None)  # unreachable: _chat_models is never empty


def _raise_rate_limit(exc: Exception) -> None:
    """Raise RateLimitError for quota/transient provider failures so
    `_chat_with_retry` can pause and resume.

    429 carries a reset timestamp when the provider sends one. Pollinations
    enforces its anonymous rate limit with 402 (Payment Required) and no
    headers, and upstream hiccups show up as 5xx — both are retried with
    exponential backoff instead of failing the whole job."""
    from openai import APIConnectionError as OpenAIConn
    from openai import RateLimitError as OpenAILimit

    if not isinstance(exc, OpenAILimit):
        status = getattr(exc, "status_code", None)
        if isinstance(exc, OpenAIConn) or status in TRANSIENT_STATUSES:
            raise RateLimitError(None) from exc
        return
    reset_ms = None
    try:
        body = exc.body if isinstance(exc.body, dict) else {}
        meta = body.get("error", {}).get("metadata", {})
        reset_ms = int(meta.get("headers", {}).get("X-RateLimit-Reset", 0) or 0) or None
        if not reset_ms:
            reset_ms = int(exc.headers.get("x-ratelimit-reset", 0) or 0) or None
    except (AttributeError, TypeError, ValueError):
        reset_ms = None
    raise RateLimitError(reset_ms) from exc


def _chat_with_retry(
    system: str,
    user: str,
    on_pause: OnPause = None,
    _attempt: int = 0,
    model: type[T] | None = None,
) -> str | list[T]:
    """Cached LLM call; on a rate limit, transient provider error, or
    unparseable answer, sleep and retry, keeping the caller's generator
    alive so progress resumes in place. Identical requests replay from the
    cache for free.

    When `model` is given, parsing happens here via `_extract_json`, so only
    valid JSON ever reaches the caller or the cache — pollinations
    intermittently answers 200 OK with a string cut off mid-output, which
    used to kill the whole job with an uncaught JSONDecodeError and then
    poison the cache with the broken response."""
    key = _cache_key(system, user)
    while True:
        cached = db.llm_cache_get(key)
        if cached is not None:
            if model is None:
                return cached
            try:
                return _extract_json(cached, model)
            except ValueError:
                pass  # poisoned cache entry: re-ask fresh, overwrite on success
        try:
            content = _chat(system, user)
            parsed = _extract_json(content, model) if model is not None else content
            db.llm_cache_put(key, content)
            return parsed
        except RateLimitError as exc:
            if exc.reset_ts_ms:
                wait = exc.reset_ts_ms / 1000.0 - time.time() + RETRY_PAD_SECONDS
            else:
                if _attempt >= MAX_BACKOFF_RETRIES:
                    raise  # no reset time and still limited: give up
                wait = min(5.0 * (2 ** _attempt), 60.0)
            wait = max(wait, 3.0)
            if wait > MAX_PAUSE_SECONDS:
                raise
            if on_pause:
                on_pause(exc.reset_ts_ms, wait)
            time.sleep(wait)
            if on_pause:
                on_pause(None, 0.0)  # back in business
            _attempt += 1
        except ValueError as exc:
            # 200 OK but truncated/garbled JSON: transient model failure.
            # Never cached (the cache write sits after the parse above).
            if _attempt >= MAX_BAD_JSON_RETRIES:
                raise RuntimeError(
                    "Model returned incomplete JSON after several retries; try again."
                ) from exc
            wait = max(3.0, min(5.0 * (2 ** _attempt), 60.0))
            if on_pause:
                on_pause(None, wait)
            time.sleep(wait)
            if on_pause:
                on_pause(None, 0.0)
            _attempt += 1


def generate_flashcards_iter(
    source_text: str, num_cards: int, tags: list[str], on_pause: OnPause = None
) -> Iterator[list[Flashcard]]:
    """Yield batches of unique new flashcards until the target is reached.

    Whole-book sources are split into chunks and each chunk is mined with its
    own budget, so cards come from across the entire book, not just the first
    40k characters. Dedupe spans chunks."""
    if _mock_mode():
        yield _merge_tags([Flashcard.model_validate(c) for c in MOCK_FLASHCARDS], tags)[:num_cards]
        return
    cards: list[Flashcard] = []
    chunks = _split_chunks(source_text)
    for ci, chunk in enumerate(chunks):
        if len(cards) >= num_cards:
            break
        per_chunk = _ceil_div(num_cards - len(cards), len(chunks) - ci)
        for _ in range(MAX_REFILLS_PER_CHUNK):
            if len(cards) >= num_cards:
                break
            need = min(MAX_CARDS_PER_CALL, per_chunk, num_cards - len(cards))
            user = (
                f"Source material (between START and END):\nSTART\n{chunk}\nEND\n\n"
                f"Generate flashcards following the schema."
                + _already_generated_cards(cards)
                + f"Return a JSON array of exactly {need} distinct new cards "
                "(no more, no fewer)."
            )
            batch = _chat_with_retry(FLASHCARD_SYSTEM, user, on_pause, model=Flashcard)
            known = {_card_key(x) for x in cards}
            added = [c for c in batch if _card_key(c) not in known][:per_chunk]
            if not added:
                break  # model produced nothing new; stop refilling this chunk
            cards += added
            yield _merge_tags(added, tags)


def generate_mcqs_iter(
    source_text: str, num_mcqs: int, tags: list[str], on_pause: OnPause = None
) -> Iterator[list[MCQ]]:
    """Yield batches of unique new MCQs until the target is reached (chunked
    like flashcards, so a whole book is covered)."""
    if _mock_mode():
        yield _merge_tags([MCQ.model_validate(m) for m in MOCK_MCQS], tags)[:num_mcqs]
        return
    mcqs: list[MCQ] = []
    chunks = _split_chunks(source_text)
    for ci, chunk in enumerate(chunks):
        if len(mcqs) >= num_mcqs:
            break
        per_chunk = _ceil_div(num_mcqs - len(mcqs), len(chunks) - ci)
        for _ in range(MAX_REFILLS_PER_CHUNK):
            if len(mcqs) >= num_mcqs:
                break
            need = min(MAX_MCQS_PER_CALL, per_chunk, num_mcqs - len(mcqs))
            user = (
                f"Source material (between START and END):\nSTART\n{chunk}\nEND\n\n"
                f"Generate multiple-choice questions following the schema."
                + _already_generated_mcqs(mcqs)
                + f"Return a JSON array of exactly {need} distinct new questions "
                "(no more, no fewer)."
            )
            batch = _chat_with_retry(MCQ_SYSTEM, user, on_pause, model=MCQ)
            known = {_mcq_key(m) for m in mcqs}
            added = [m for m in batch if _mcq_key(m) not in known][:per_chunk]
            if not added:
                break
            mcqs += added
            yield _merge_tags(added, tags)


def _mock_mode() -> bool:
    s = get_settings()
    return s.mock_llm or not s.openai_api_key


def _card_key(c: Flashcard) -> str:
    return (c.text or c.front).strip().lower()


def _mcq_key(m: MCQ) -> str:
    return m.question.strip().lower()


def _split_chunks(text: str, size: int = 35000) -> list[str]:
    """Split source into roughly `size`-char chunks (whole-book support)."""
    text = text.strip()
    return [text[i : i + size] for i in range(0, len(text), size)] or [""]


def _ceil_div(a: int, b: int) -> int:
    return -(-a // b) if b else a


def _already_generated_cards(cards: list[Flashcard]) -> str:
    if not cards:
        return ""
    fronts = "; ".join((c.text or c.front)[:60] for c in cards[-15:])
    return f"\nAlready generated (do NOT repeat these): {fronts}."


def _already_generated_mcqs(mcqs: list[MCQ]) -> str:
    if not mcqs:
        return ""
    questions = "; ".join(m.question[:60] for m in mcqs[-15:])
    return f"\nAlready generated (do NOT repeat these): {questions}."


def _merge_tags(items: list, tags: list[str]) -> list:
    for item in items:
        for t in tags:
            if t not in item.tags:
                item.tags.append(t)
    return items
