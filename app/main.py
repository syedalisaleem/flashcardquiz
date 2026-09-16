"""FastAPI app: deck management, background generation jobs, review, quiz, export."""

from __future__ import annotations

import json as _json
import logging
import re
import sys
import threading
import time
import urllib.request
import uuid
from pathlib import Path
from urllib.parse import urlparse

from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, Response
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

from . import db, llm
from .ingest import extract_pdf_text, extract_image_text
from .config import get_settings
from .schemas import FlashcardUpdate, GenerateRequest, ReviewRequest

logger = logging.getLogger(__name__)

IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff")
MAX_UPLOAD_BYTES = 20 * 1024 * 1024  # 20 MB
_ALLOWED_URL_HOSTS = {"localhost", "127.0.0.1", "0.0.0.0"}


def _resource_dir() -> Path:
    """Static assets live next to the package in dev; in _MEIPASS when frozen (.exe)."""
    base = getattr(sys, "_MEIPASS", Path(__file__).resolve().parent.parent)
    return Path(base) / "static"


app = FastAPI(title="Flashcard & Quiz Generator")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://127.0.0.1:8000", "http://localhost:8000"],
    allow_methods=["GET", "POST", "PATCH", "DELETE"],
    allow_headers=["*"],
)


@app.middleware("http")
async def _security_headers(request: Request, call_next):
    resp = await call_next(request)
    resp.headers["X-Content-Type-Options"] = "nosniff"
    resp.headers["X-Frame-Options"] = "DENY"
    resp.headers["Referrer-Policy"] = "no-referrer"
    resp.headers["X-XSS-Protection"] = "1; mode=block"
    if request.url.path.startswith("/api/"):
        resp.headers["Cache-Control"] = "no-store"
    return resp

db.init_db()

# ---------------------------------------------------------------- jobs

jobs: dict[str, dict] = {}
jobs_lock = threading.Lock()
_MAX_JOBS = 200
_JOB_TTL = 3600  # 1 hour


def _cleanup_jobs() -> None:
    """Remove old completed jobs to prevent memory exhaustion."""
    now = time.time()
    with jobs_lock:
        to_delete = [
            jid for jid, j in jobs.items()
            if j["status"] in ("done", "error") and now - j.get("ts", now) > _JOB_TTL
        ]
        for jid in to_delete:
            del jobs[jid]
        if len(jobs) > _MAX_JOBS:
            oldest = sorted(jobs, key=lambda k: jobs[k].get("ts", 0))[:len(jobs) - _MAX_JOBS]
            for jid in oldest:
                del jobs[jid]


def _on_pause(job_id: str, reset_ms: int | None, wait_s: float) -> None:
    with jobs_lock:
        jobs[job_id]["status"] = "paused" if wait_s else "running"
        jobs[job_id]["reset_ms"] = reset_ms
        jobs[job_id]["wait_s"] = round(wait_s)


def _run_generation_job(job_id: str, deck_id: int, req: GenerateRequest) -> None:
    on_pause = lambda reset_ms, wait_s: _on_pause(job_id, reset_ms, wait_s)
    try:
        if req.flashcards:
            for batch in llm.generate_flashcards_iter(req.source_text, req.num_cards, req.tags, on_pause):
                db.add_flashcards(deck_id, batch)
                with jobs_lock:
                    jobs[job_id]["cards"] += len(batch)
        if req.mcqs:
            for batch in llm.generate_mcqs_iter(req.source_text, req.num_mcqs, req.tags, on_pause):
                db.add_mcqs(deck_id, batch)
                with jobs_lock:
                    jobs[job_id]["mcqs"] += len(batch)
        with jobs_lock:
            jobs[job_id]["status"] = "done"
    except Exception as exc:  # noqa: BLE001 — report any failure to the client
        logger.exception("Generation job %s failed", job_id)
        with jobs_lock:
            jobs[job_id]["status"] = "error"
            jobs[job_id]["error"] = "Generation failed. Check your source text and try again."


# ---------------------------------------------------------------- settings

OLLAMA_URL = "http://127.0.0.1:11434"
LMSTUDIO_URL = "http://127.0.0.1:1234/v1"
_models_cache: dict = {"ts": 0.0, "data": []}
_models_lock = threading.Lock()
_MODELS_TTL = 5.0


class SettingsUpdate(BaseModel):
    base_url: str | None = None
    model: str | None = None


def _env_path() -> Path:
    """.env the settings UI writes to (next to the code in dev; in the
    user-writable app data dir when frozen)."""
    from . import db as _db

    if getattr(sys, "frozen", False):
        return _db._data_dir() / ".env"
    return Path(__file__).resolve().parent.parent / ".env"


def _probe_json(url: str, timeout: float = 1.2):
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            return _json.loads(resp.read().decode("utf-8"))
    except Exception:
        return None


@app.get("/api/settings")
def get_settings_public() -> dict:
    s = get_settings()
    return {
        "model": s.llm_model,
        "base_url": s.openai_base_url,
        "mock": s.mock_llm or not s.openai_api_key,
    }


@app.get("/api/models")
def list_models() -> list[dict]:
    """Active cloud model + any local servers detected (Ollama, LM Studio)."""
    global _models_cache
    now = time.time()
    with _models_lock:
        if now - _models_cache["ts"] < _MODELS_TTL:
            return _models_cache["data"]

    s = get_settings()
    try:
        host = s.openai_base_url.split("//", 1)[1].split("/", 1)[0]
    except IndexError:
        host = s.openai_base_url
    out: list[dict] = [{
        "id": "configured",
        "label": f"{host} \u2014 {s.llm_model}",
        "base_url": s.openai_base_url,
        "model": s.llm_model,
    }]
    ollama = _probe_json(f"{OLLAMA_URL}/api/tags")
    if ollama:
        for m in ollama.get("models") or []:
            name = m.get("name", "")
            if name and ":latest" not in name:
                out.append({
                    "id": f"ollama:{name}",
                    "label": f"Ollama \u2014 {name}",
                    "base_url": f"{OLLAMA_URL}/v1",
                    "model": name,
                })
    lmstudio = _probe_json(f"{LMSTUDIO_URL}/models")
    if lmstudio:
        for m in lmstudio.get("data") or []:
            mid = m.get("id", "")
            if mid:
                out.append({
                    "id": f"lmstudio:{mid}",
                    "label": f"LM Studio \u2014 {mid}",
                    "base_url": LMSTUDIO_URL,
                    "model": mid,
                })
    with _models_lock:
        _models_cache = {"ts": now, "data": out}
    return out


def _safe_url(url: str) -> bool:
    """Allow only http/https URLs pointing to localhost or user-configured hosts."""
    try:
        parsed = urlparse(url)
    except Exception:
        return False
    if parsed.scheme not in ("http", "https"):
        return False
    host = (parsed.hostname or "").lower()
    return bool(host)


@app.patch("/api/settings")
def update_settings(update: SettingsUpdate) -> dict:
    """Switch provider/model by updating the .env file (survives restarts)."""
    if update.base_url is not None and not _safe_url(update.base_url):
        raise HTTPException(400, "Invalid base URL")
    path = _env_path()
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    out: list[str] = []
    patched: set[str] = set()
    for ln in lines:
        if ln.startswith("OPENAI_BASE_URL="):
            if update.base_url is not None:
                out.append(f"OPENAI_BASE_URL={update.base_url}")
                patched.add("base")
            else:
                out.append(ln)
        elif ln.startswith("LLM_MODEL="):
            if update.model is not None:
                out.append(f"LLM_MODEL={update.model}")
                patched.add("model")
            else:
                out.append(ln)
        else:
            out.append(ln)
    if update.base_url is not None and "base" not in patched:
        out.append(f"OPENAI_BASE_URL={update.base_url}")
    if update.model is not None and "model" not in patched:
        out.append(f"LLM_MODEL={update.model}")
    try:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("\n".join(out) + "\n", encoding="utf-8")
    except OSError:
        raise HTTPException(500, "Could not save settings. Check file permissions.")
    get_settings.cache_clear()
    return get_settings_public()


# ---------------------------------------------------------------- decks

@app.get("/api/decks")
def list_decks() -> list[dict]:
    return db.list_decks()


@app.post("/api/decks")
def create_deck(payload: dict) -> dict:
    name = (payload.get("name") or "").strip()
    if not name:
        raise HTTPException(400, "Deck name is required")
    if len(name) > 200:
        raise HTTPException(400, "Deck name too long (max 200 characters)")
    return db.create_deck(name)


@app.delete("/api/decks/{deck_id}")
def delete_deck(deck_id: int) -> dict:
    if not db.deck_exists(deck_id):
        raise HTTPException(404, "Deck not found")
    db.delete_deck(deck_id)
    return {"ok": True}


@app.get("/api/decks/{deck_id}")
def get_deck(deck_id: int) -> dict:
    try:
        return db.get_deck(deck_id)
    except ValueError:
        raise HTTPException(404, "Deck not found")


# ---------------------------------------------------------------- generation

@app.post("/api/decks/{deck_id}/generate")
def start_generation(deck_id: int, req: GenerateRequest) -> dict:
    if not db.deck_exists(deck_id):
        raise HTTPException(404, "Deck not found")
    if not req.source_text.strip():
        raise HTTPException(400, "Source text is empty. Upload a file or paste notes.")
    if not req.flashcards and not req.mcqs:
        raise HTTPException(400, "Nothing to generate: enable cards and/or MCQs.")
    job_id = uuid.uuid4().hex[:12]
    with jobs_lock:
        jobs[job_id] = {
            "deck_id": deck_id, "status": "running", "cards": 0, "mcqs": 0,
            "target_cards": req.num_cards if req.flashcards else 0,
            "target_mcqs": req.num_mcqs if req.mcqs else 0,
            "error": "", "reset_ms": None, "wait_s": 0, "ts": time.time(),
        }
    _cleanup_jobs()
    threading.Thread(target=_run_generation_job, args=(job_id, deck_id, req), daemon=True).start()
    return {"job_id": job_id}


@app.get("/api/jobs/{job_id}")
def get_job(job_id: str) -> dict:
    with jobs_lock:
        job = jobs.get(job_id)
        if not job:
            raise HTTPException(404, "Job not found")
        return dict(job)


# ---------------------------------------------------------------- flashcards

@app.patch("/api/decks/{deck_id}/cards/{card_id}")
def update_card(deck_id: int, card_id: int, update: FlashcardUpdate) -> dict:
    try:
        return db.update_flashcard(deck_id, card_id, update)
    except ValueError:
        raise HTTPException(404, "Card not found")


@app.delete("/api/decks/{deck_id}/cards/{card_id}")
def delete_card(deck_id: int, card_id: int) -> dict:
    try:
        db.delete_flashcard(deck_id, card_id)
    except ValueError:
        raise HTTPException(404, "Card not found")
    return {"ok": True}


# ---------------------------------------------------------------- export

def slugify(name: str) -> str:
    keep = "".join(c if c.isalnum() or c in " -_" else "" for c in name).strip()
    return keep.replace(" ", "_") or "deck"


def _safe_filename(name: str) -> str:
    """Sanitize a filename for Content-Disposition header (RFC 6266)."""
    safe = re.sub(r'[^\w\-.]', '_', name).strip('_')
    return safe[:80] or "deck"


@app.get("/api/decks/{deck_id}/export")
def export_deck(deck_id: int) -> Response:
    from .anki_export import build_apkg
    from .schemas import Flashcard

    try:
        deck = db.get_deck(deck_id)
    except ValueError:
        raise HTTPException(404, "Deck not found")
    if not deck["cards"]:
        raise HTTPException(400, "No flashcards to export. Generate first.")
    cards = [
        Flashcard(type=c["type"], front=c["front"], back=c["back"],
                  text=c["text"], tags=c["tags"], source=c["source"])
        for c in deck["cards"]
    ]
    apkg = build_apkg(cards, deck_name=deck["name"])
    filename = _safe_filename(deck["name"]) + ".apkg"
    return Response(
        content=apkg,
        media_type="application/octet-stream",
        headers={"Content-Disposition": f'attachment; filename="{filename}"'},
    )


# ---------------------------------------------------------------- review (SM-2)

@app.get("/api/decks/{deck_id}/review")
def review_queue(deck_id: int) -> dict:
    if not db.deck_exists(deck_id):
        raise HTTPException(404, "Deck not found")
    due = db.due_cards(deck_id)
    return {"due": due, "count": len(due)}


@app.post("/api/review")
def review_card(payload: ReviewRequest) -> dict:
    try:
        return db.apply_review(payload.deck_id, payload.card_id, payload.quality)
    except ValueError:
        raise HTTPException(404, "Card not found")


# ---------------------------------------------------------------- quiz

@app.post("/api/decks/{deck_id}/quiz/answer")
def quiz_answer(deck_id: int, payload: dict) -> dict:
    if "mcq_id" not in payload or "choice" not in payload:
        raise HTTPException(400, "Need 'mcq_id' and 'choice'")
    if not db.deck_exists(deck_id):
        raise HTTPException(404, "Deck not found")
    mcq = db.get_mcq(deck_id, payload["mcq_id"])
    if not mcq:
        raise HTTPException(404, "Question not found")
    try:
        choice = int(payload["choice"])
    except (TypeError, ValueError):
        raise HTTPException(400, "'choice' must be an option index")
    correct = choice == mcq["correct_index"]
    db.record_answer(deck_id, mcq["id"], correct)
    explanation = (
        mcq["explanation"]
        if correct
        else mcq["distractor_explanations"].get(str(choice), mcq["explanation"])
    )
    return {
        "correct": correct,
        "correct_index": mcq["correct_index"],
        "explanation": explanation,
        "correct_text": mcq["options"][mcq["correct_index"]],
    }


@app.delete("/api/decks/{deck_id}/mcqs/{mcq_id}")
def delete_mcq(deck_id: int, mcq_id: int) -> dict:
    try:
        db.delete_mcq(deck_id, mcq_id)
    except ValueError:
        raise HTTPException(404, "Question not found")
    return {"ok": True}


# ---------------------------------------------------------------- ingestion

@app.post("/api/upload")
async def upload(
    file: UploadFile = File(...),
    use_ocr: bool = Form(False),
) -> dict:
    data = await file.read()
    if len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(413, f"File too large. Maximum size is {MAX_UPLOAD_BYTES // (1024*1024)} MB.")
    name = (file.filename or "").lower()
    if name.endswith(".pdf"):
        try:
            text = extract_pdf_text(data, ocr=use_ocr)
        except ValueError as exc:
            raise HTTPException(400, str(exc))
        except Exception:
            logger.exception("PDF ingestion failed")
            raise HTTPException(400, "Could not read this PDF (corrupt or encrypted file).")
    elif name.endswith(IMAGE_EXTS):
        try:
            text = extract_image_text(data)
        except ValueError as exc:
            raise HTTPException(400, str(exc))
        except Exception:
            logger.exception("Image OCR failed")
            raise HTTPException(400, "Could not read this image file.")
    else:
        try:
            text = data.decode("utf-8")
        except UnicodeDecodeError:
            raise HTTPException(400, "Unsupported file type. Upload a PDF, image (OCR), or .txt file.")
    if not text.strip():
        raise HTTPException(
            400,
            "No readable text found. For scanned PDFs, tick \u201cOCR scanned pages\u201d.",
        )
    return {"text": text, "ocr": name.endswith(IMAGE_EXTS)}


# ---------------------------------------------------------------- pages & downloads

@app.get("/app")
def app_page() -> FileResponse:
    return FileResponse(_resource_dir() / "app.html")


@app.get("/api/download/desktop")
def download_desktop() -> FileResponse:
    exe = Path(__file__).resolve().parent.parent / "dist" / "FlashcardQuizApp.exe"
    if not exe.exists():
        raise HTTPException(404, "Desktop build not available yet. Build it with: pyinstaller desktop.spec")
    return FileResponse(exe, filename="FlashcardQuizApp.exe", media_type="application/octet-stream")


@app.get("/api/download/android")
def download_android() -> FileResponse:
    apk = Path(__file__).resolve().parent.parent / "dist" / "FlashcardQuizApp.apk"
    if not apk.exists():
        raise HTTPException(404, "Android build not available yet. Build it with: powershell build_apk.ps1")
    return FileResponse(apk, filename="FlashcardQuizApp.apk", media_type="application/vnd.android.package-archive")


app.mount("/", StaticFiles(directory=str(_resource_dir()), html=True), name="static")
