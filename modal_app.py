"""Modal deployment for FlashcardQuiz.

Architecture notes (why the knobs are what they are):

* The FastAPI app keeps generation jobs in an in-process dict
  (app/main.py: `jobs` + a daemon `threading.Thread`) and SQLite is a single
  connection guarded by a `threading.Lock` (app/db.py). Both are single-process
  only, so the web function is pinned to exactly one container:
  `max_containers=1`. Every request and every `/api/jobs/{id}` poll therefore
  lands on the same container.
* SQLite lives on a Modal Volume mounted at /data (`FLASHCARDQUIZ_DATA_DIR`).
  Modal background-commits Volumes every few seconds and again on container
  shutdown, so writes survive scale-to-zero. Single writer = safe.
* `scaledown_window` keeps the container alive after the last request so a
  background generation job (and its progress polling) can finish before the
  container scales down.

Deploy:   modal deploy modal_app.py
Dev:      modal serve modal_app.py
"""

from __future__ import annotations

import sys

import modal

APP_NAME = "flashcardquiz"

# Everything the app reads from the environment (app/config.py -> pydantic-settings).
# Real environment variables beat the (absent) .env file in the container.
ENV = {
    # Keyless OpenAI-compatible endpoint.
    "OPENAI_API_KEY": "free",
    "OPENAI_BASE_URL": "https://text.pollinations.ai/openai",
    "LLM_MODEL": "openai-fast",
    "MOCK_LLM": "false",
    # OCR: local RapidOCR first, ocr.space as the fallback.
    "OCR_PROVIDER": "auto",
    "OCRSPACE_API_KEY": "K84356707588957",
    "MAX_UPLOAD_MB": "50",
    "CORS_ORIGINS": "",
    # SQLite on the Volume, not in the image.
    "FLASHCARDQUIZ_DATA_DIR": "/data",
}

image = (
    modal.Image.debian_slim(python_version="3.12")
    .pip_install_from_requirements("requirements.txt")
    .env(ENV)
    .add_local_dir("app", "/pkg/app", ignore=["**/__pycache__", "**/data.db"])
    .add_local_dir("static", "/pkg/static")
    # /api/download/android + /api/download/desktop serve from here.
    .add_local_dir("dist", "/pkg/dist", ignore=["*.aab", "*.zip"])
)

data_volume = modal.Volume.from_name("flashcardquiz-data", create_if_missing=True)

app = modal.App(APP_NAME, image=image)


@app.function(
    volumes={"/data": data_volume},
    cpu=0.5,
    memory=1024,
    # Single process: in-memory job registry + one SQLite connection.
    max_containers=1,
    # Keep the container up long enough for generation jobs + polling.
    scaledown_window=600,
    min_containers=0,
    # Long PDF/OCR uploads must not be cut mid-request.
    timeout=1800,
)
@modal.concurrent(max_inputs=20)
@modal.asgi_app()
def web():
    sys.path.insert(0, "/pkg")
    from app.main import app as fastapi_app

    return fastapi_app
