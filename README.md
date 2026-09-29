# Flashcard & Quiz Generator

Multi-modal study tool: ingestion (PDF/text) → LLM extraction (Minimum Information
Principle flashcards + MCQ with misconception distractors) → Anki `.apkg` export
or in-app SM-2 spaced repetition.

## Apps

| Platform | How to get it |
|---|---|
| **Windows desktop** | `dist/FlashcardQuizApp.exe` (standalone, no Python needed) or the "Download the app" button on the landing page |
| **Mobile (Android / iPhone)** | Open `http://<host>:8000/app` in the phone browser → Share → *Add to Home Screen* — installable PWA with offline shell |
| **Browser** | `http://localhost:8000` (landing) or `/app` (workspace) |

The desktop exe bundles the server + UI in one native window (pywebview).
Its data lives in `%LOCALAPPDATA%\FlashcardQuiz\data.db`; put a `.env` next to the
exe to configure an API key.

Rebuild the exe: `pyinstaller desktop.spec` (PyInstaller must be installed).

## Features

- **Whole books** — PDFs up to 5,000 pages ingest fully; generation chunks
  the source (35k chars) and mines cards across every chunk, so a 600-page
  textbook's later chapters get covered, not just the first pages
- **OCR built in** — scanned PDFs and photos are read locally with RapidOCR
  (rendered via PDFium, no cloud calls): tick "OCR scanned pages" on upload, or
  upload a PNG/JPEG/WEBP/BMP/TIFF directly
- **Background generation** — cards are created in a worker thread; watch them
  appear live with a progress bar (refill loop handles models that return one
  item per call)
- **Quota resilience** — responses are cached (regenerating the same source is
  free), and on a 429 the job pauses with an auto-resume countdown until the
  daily quota resets instead of failing. Add 10 credits on OpenRouter to raise
  the free tier to 1000 requests/day
- **Atomic cards** — Basic + Cloze (rendered with highlights), tags, source
  grounding (page/slide refs)
- **MCQ quiz** — misconception-based distractors with per-option explanations,
  per-question accuracy tracking, session score
- **SM-2 spaced repetition** — in-app review queue with keyboard shortcuts (1–4)
- **Anki export** — `.apkg` with Basic + Cloze models, tags, and source refs

## Setup

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env   # then edit .env
```

Without an API key, set `MOCK_LLM=true` to use deterministic sample data.

## Run

```powershell
uvicorn app.main:app --reload
```

Open http://127.0.0.1:8000 · App UI: http://127.0.0.1:8000/app

## Tests

```powershell
$env:MOCK_LLM = "true"; python test_app.py   # full suite
python test_live.py                          # live-LLM test (needs API key + quota)
```

## API

| Endpoint | Purpose |
|---|---|
| `GET /api/settings` | Active model / mock mode |
| `GET /api/download/desktop` | Downloads `FlashcardQuizApp.exe` |
| `GET·POST /api/decks`, `DELETE /api/decks/{id}` | Deck management |
| `GET /api/decks/{id}` | Deck with cards + MCQs |
| `POST /api/decks/{id}/generate` | Start background generation (returns job id) |
| `GET /api/jobs/{job_id}` | Generation progress |
| `PATCH·DELETE /api/decks/{id}/cards/{card_id}` | Edit / remove a flashcard |
| `DELETE /api/decks/{id}/mcqs/{mcq_id}` | Remove a question |
| `GET /api/decks/{id}/export` | Download `.apkg` for Anki |
| `GET /api/decks/{id}/review`, `POST /api/review` | SM-2 due queue + grading (0–5) |
| `POST /api/decks/{id}/quiz/answer` | Answer a question (updates stats) |
| `POST /api/upload` | Upload PDF / TXT / image — OCR scans (`use_ocr` flag), returns extracted text |

## Use a local AI model (free, offline, no quota)

The app detects local LLM servers automatically — open the **Generate** tab and
pick a model from the dropdown (shown as `… (local)`).

**Ollama** (easiest, ~3 GB for a small model):

```powershell
winget install Ollama.Ollama        # or https://ollama.com
ollama pull gemma3:4b               # small, runs on 8 GB RAM
# the app finds it at http://127.0.0.1:11434 automatically
```

**LM Studio**: start the local server (Developer tab → Start Server), and its
loaded models appear in the same dropdown (`http://127.0.0.1:1234/v1`).

Switching writes `OPENAI_BASE_URL`/`LLM_MODEL` to `.env`, so it survives
restarts. Any OpenAI-compatible endpoint works; set it manually in `.env` if
you run something else.

## Configuration (`.env`)

```
# OpenCode Zen — "DeepSeek V4 Flash (free)"
OPENAI_API_KEY=sk-...
OPENAI_BASE_URL=https://opencode.ai/zen/v1
LLM_MODEL=deepseek-v4-flash-free
MOCK_LLM=false
```

Any OpenAI-compatible endpoint works (OpenRouter, OpenCode Zen, Ollama,
LM Studio, NVIDIA NIM, Gemini...). The stack is provider-agnostic — audio
ingestion (Whisper) and vision/OCR are natural next extensions of
`app/ingest.py`.
