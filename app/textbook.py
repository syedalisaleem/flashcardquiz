"""Textbook/curriculum fetch for study requests.

`fetch_textbook(prompt, class_name, school, city, country)` returns
`(text, provider)` where provider is one of:

    "firecrawl" — researched study notes from the web (Firecrawl v2
                  /agent, falling back to /v2/search) when FIRECRAWL_API_KEY
                  is set;
    "gemini"    — study notes written by Gemini's own curriculum knowledge
                  (direct generateContent call) when GEMINI_API_KEY is set;
    "prompt"    — the brief itself, always available, no network.

Results are cached in-process for an hour (single container deployment).
"""

from __future__ import annotations

import hashlib
import json
import logging
import threading
import time
import urllib.error
import urllib.request

from .config import get_settings

logger = logging.getLogger(__name__)

CACHE_TTL_S = 3600
_MAX_CHARS = 60_000  # plenty for 200 cards + 100 MCQs
_GEMINI_ROOT = "https://generativelanguage.googleapis.com/v1beta/models"

_cache: dict[str, tuple[float, str, str]] = {}
_cache_lock = threading.Lock()


class FetchError(Exception):
    """Raised only if a provider responds but with an unusable payload."""


def _post_json(url: str, payload: dict, headers: dict, timeout: float):
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _cap(text: str) -> str:
    text = text.strip()
    if len(text) > _MAX_CHARS:
        text = text[:_MAX_CHARS]
    return text


def _brief(prompt: str, class_name: str, school: str, city: str, country: str) -> str:
    parts = [prompt]
    ctx = [v for v in (class_name, school, city, country) if v]
    if ctx:
        parts.append("Context: " + ", ".join(ctx))
    return "\n".join(parts)


# ------------------------------------------------------------------ Firecrawl

def _agent_text(resp) -> str:
    """Pull the researched markdown out of a /v2/agent response."""
    candidates = []
    if isinstance(resp, dict):
        data = resp.get("data")
        if isinstance(data, str):
            candidates.append(data)
        elif isinstance(data, dict):
            candidates += [data.get(k) for k in ("result", "output", "content", "text")]
        candidates += [resp.get(k) for k in ("result", "output", "text")]
        messages = resp.get("messages")
        if isinstance(messages, list):
            for m in reversed(messages):
                if isinstance(m, dict):
                    content = m.get("content") or m.get("text")
                    if isinstance(content, str):
                        candidates.append(content)
                        break
    for c in candidates:
        if isinstance(c, str) and c.strip():
            return c
    return ""


def _search_text(resp) -> str:
    """Concatenate markdown from a /v2/search response (shape-tolerant)."""
    items = []
    if isinstance(resp, dict):
        data = resp.get("data")
        if isinstance(data, list):
            items = data
        elif isinstance(data, dict):
            for k in ("web", "results", "data"):
                if isinstance(data.get(k), list):
                    items = data[k]
                    break
        if not items and isinstance(resp.get("results"), list):
            items = resp["results"]
    parts = []
    for it in items[:6]:
        if not isinstance(it, dict):
            continue
        title = str(it.get("title") or "").strip()
        md = it.get("markdown") or it.get("content") or it.get("description") or ""
        if not isinstance(md, str) or not md.strip():
            continue
        parts.append(f"{title}\n{md.strip()}" if title else md.strip())
    return "\n\n---\n\n".join(parts)


def _firecrawl(settings, brief: str) -> str:
    key = settings.firecrawl_api_key.strip()
    if not key:
        return ""
    headers = {"Authorization": f"Bearer {key}"}
    base = settings.firecrawl_base_url.rstrip("/")

    try:
        resp = _post_json(
            f"{base}/agent",
            {
                "prompt": (
                    "Research and write comprehensive textbook study notes for the "
                    "student described below. Prefer the curriculum and textbooks "
                    "actually used for that class and country. Include facts, "
                    "definitions, dates, formulas, worked examples and headings. "
                    "Return only the notes as markdown.\n\n" + brief
                ),
                "effort": "low",
            },
            headers,
            timeout=75,
        )
        text = _cap(_agent_text(resp))
        if text:
            return text
    except Exception as exc:  # noqa: BLE001 — provider is best-effort
        logger.info("firecrawl agent failed: %s", exc)

    try:
        resp = _post_json(
            f"{base}/search",
            {"query": brief[:300], "limit": 6},
            headers,
            timeout=35,
        )
        text = _cap(_search_text(resp))
        if text:
            return text
    except Exception as exc:  # noqa: BLE001
        logger.info("firecrawl search failed: %s", exc)
    return ""


# -------------------------------------------------------------------- Gemini

def _gemini_text(settings, prompt: str, max_tokens: int, timeout: float) -> str:
    key = settings.gemini_api_key.strip()
    if not key:
        return ""
    models = []
    primary = (settings.gemini_model or "").strip() or "gemini-3.5-flash"
    models.append(primary)
    for m in ("gemini-3.5-flash-lite", "gemini-3.6-flash"):
        if m not in models:
            models.append(m)
    payload = {
        "contents": [{"parts": [{"text": prompt}]}],
        "generationConfig": {"temperature": 0.3, "maxOutputTokens": max_tokens},
    }
    for model in models:
        resp = None
        for attempt in (0, 1):
            try:
                resp = _post_json(
                    f"{_GEMINI_ROOT}/{model}:generateContent",
                    payload,
                    {"x-goog-api-key": key},
                    timeout=timeout,
                )
                break
            except urllib.error.HTTPError as exc:
                if exc.code in (429, 503) and attempt == 0:
                    time.sleep(3)
                    continue
                logger.info("gemini %s -> HTTP %s", model, exc.code)
                break
            except Exception as exc:  # noqa: BLE001
                logger.info("gemini %s failed: %s", model, exc)
                break
        else:
            continue
        if not isinstance(resp, dict):
            continue
        cands = resp.get("candidates") or []
        if not cands:
            continue
        parts = (cands[0].get("content") or {}).get("parts") or []
        text = "".join(
            p.get("text", "") for p in parts if isinstance(p, dict)
        ).strip()
        if text:
            return text
    return ""


def _gemini_knowledge(settings, brief: str) -> str:
    prompt = (
        "You are a curriculum expert. Write comprehensive, accurate study notes "
        "for the topic below as if transcribed from the student's actual "
        "textbook. Cover the key facts, definitions, dates, formulas, examples "
        "and typical exam points, organized with clear markdown headings. "
        "Do not include any preamble.\n\n" + brief
    )
    return _cap(_gemini_text(settings, prompt, max_tokens=4096, timeout=60))


# --------------------------------------------------------------------- entry

def fetch_textbook(
    prompt: str,
    class_name: str = "",
    school: str = "",
    city: str = "",
    country: str = "",
) -> tuple[str, str]:
    brief = _brief(
        prompt.strip(),
        (class_name or "").strip(),
        (school or "").strip(),
        (city or "").strip(),
        (country or "").strip(),
    )
    cache_key = hashlib.sha256(brief.lower().encode("utf-8")).hexdigest()
    now = time.time()
    with _cache_lock:
        hit = _cache.get(cache_key)
        if hit and now - hit[0] < CACHE_TTL_S:
            return hit[1], hit[2]

    settings = get_settings()
    text, provider = "", "prompt"

    if settings.firecrawl_api_key.strip():
        try:
            text = _firecrawl(settings, brief)
        except Exception:  # noqa: BLE001 — keep falling through
            logger.exception("firecrawl fetch failed")
        if text.strip():
            provider = "firecrawl"

    if not text.strip():
        try:
            text = _gemini_knowledge(settings, brief)
        except Exception:  # noqa: BLE001
            logger.exception("gemini knowledge failed")
        if text.strip():
            provider = "gemini"

    if not text.strip():
        text, provider = brief, "prompt"

    with _cache_lock:
        _cache[cache_key] = (now, text, provider)
        if len(_cache) > 512:
            for k in sorted(_cache, key=lambda k: _cache[k][0])[: len(_cache) - 512]:
                del _cache[k]
    return text, provider
