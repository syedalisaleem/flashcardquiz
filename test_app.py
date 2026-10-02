"""Comprehensive app test suite (run with MOCK_LLM=true or real key)."""

import io
import time

from fastapi.testclient import TestClient

from app.main import app

c = TestClient(app)


def test_landing_and_assets():
    r = c.get("/")
    assert r.status_code == 200
    assert b"Open in browser" in r.content and b"hero" in r.content
    assert b"Download" in r.content and b"manifest.json" in r.content
    assert b"themeToggle" in r.content  # landing theme toggle
    assert b"Download APK" in r.content and b"/api/download/android" in r.content
    r = c.get("/app")
    assert r.status_code == 200
    assert b"tab-generate" in r.content
    assert b"prefsBtn" in r.content and b"prefsTpl" in r.content  # settings UI
    for asset in ("/style.css", "/landing.css", "/app.js", "/landing.js",
                  "/manifest.json", "/sw.js", "/icons/icon-192.png",
                  "/icons/icon-512.png", "/icons/apple-touch-icon.png"):
        assert c.get(asset).status_code == 200, asset
    m = c.get("/manifest.json").json()
    assert m["start_url"] == "/app" and m["display"] == "standalone"
    print("landing + assets + PWA OK")


def test_desktop_download():
    r = c.get("/api/download/desktop")
    if r.status_code == 404:
        print("desktop download: build not present (skip)")
        return
    assert r.status_code == 200
    assert r.headers["content-disposition"].startswith("attachment")
    assert r.content[:2] == b"MZ"
    print("desktop download OK:", len(r.content), "bytes")


def test_android_download():
    r = c.get("/api/download/android", follow_redirects=False)
    # APK lives on GitHub Releases (CDN) — endpoint is a 302 to it
    assert r.status_code == 302
    assert r.headers["location"] == (
        "https://github.com/syedalisaleem/flashcardquiz"
        "/releases/latest/download/FlashcardQuizApp.apk"
    )
    print("android download redirect OK ->", r.headers["location"])


def test_settings_and_decks():
    s = c.get("/api/settings").json()
    assert s["model"]
    decks = c.get("/api/decks").json()
    assert len(decks) >= 1
    assert c.post("/api/decks", json={"name": "  "}).status_code == 400
    deck = c.post("/api/decks", json={"name": "Suite Deck"}).json()
    assert deck["id"]
    assert c.delete("/api/decks/999999").status_code == 404
    print("settings + decks OK")


def test_generation_job_and_persistence():
    deck = c.post("/api/decks", json={"name": "Job Deck"}).json()
    deck_id = deck["id"]
    # validations
    assert c.post(f"/api/decks/{deck_id}/generate", json={"source_text": "", "flashcards": True, "mcqs": True}).status_code == 400
    assert c.post("/api/decks/999999/generate", json={"source_text": "x", "flashcards": True, "mcqs": True}).status_code == 404
    # job
    r = c.post(
        f"/api/decks/{deck_id}/generate",
        json={
            "source_text": (
                "Mitochondria produce ATP via cellular respiration. Helicase unwinds the DNA double helix. "
                "Ribosomes synthesize proteins. The Golgi apparatus packages proteins. The nucleus stores chromatin."
            ),
            "flashcards": True, "mcqs": True,
            "num_cards": 5, "num_mcqs": 2, "tags": ["Bio", "Lecture"],
        },
    )
    assert r.status_code == 200, r.text
    job_id = r.json()["job_id"]
    job = None
    for _ in range(60):
        job = c.get(f"/api/jobs/{job_id}").json()
        if job["status"] in ("done", "error"):
            break
        time.sleep(1)
    assert job["status"] == "done", job
    assert job["cards"] == 5 and job["mcqs"] == 2, job
    # persisted deck content
    deck = c.get(f"/api/decks/{deck_id}").json()
    assert len(deck["cards"]) == 5 and len(deck["mcqs"]) == 2
    types = {card["type"] for card in deck["cards"]}
    assert types <= {"Basic", "Cloze"} and types
    assert all("Bio" in card["tags"] for card in deck["cards"])
    print("generation job + persistence OK")


def test_cards_crud():
    deck = c.get("/api/decks").json()[-1]
    deck_id = deck["id"]
    cards = c.get(f"/api/decks/{deck_id}").json()["cards"]
    card = cards[0]
    r = c.patch(f"/api/decks/{deck_id}/cards/{card['id']}", json={"back": "EDITED"})
    assert r.json()["back"] == "EDITED"
    assert c.patch(f"/api/decks/{deck_id}/cards/999999", json={"back": "x"}).status_code == 404
    assert c.delete(f"/api/decks/{deck_id}/cards/{card['id']}").status_code == 200
    assert len(c.get(f"/api/decks/{deck_id}").json()["cards"]) == len(cards) - 1
    print("cards CRUD OK")


def test_sm2_review():
    deck = c.get("/api/decks").json()[-1]
    deck_id = deck["id"]
    cards = c.get(f"/api/decks/{deck_id}").json()["cards"]
    card = cards[0]
    q = c.get(f"/api/decks/{deck_id}/review").json()
    assert q["count"] == len(cards)
    # unknown card -> 404
    assert c.post("/api/review", json={"deck_id": deck_id, "card_id": 999999, "quality": 5}).status_code == 404
    # pass twice: 1 day, then 6 days
    assert c.post("/api/review", json={"deck_id": deck_id, "card_id": card["id"], "quality": 5}).json()["interval"] == 1
    assert c.post("/api/review", json={"deck_id": deck_id, "card_id": card["id"], "quality": 4}).json()["interval"] == 6
    # fails once: interval resets to 1, ease drops
    res = c.post("/api/review", json={"deck_id": deck_id, "card_id": card["id"], "quality": 1}).json()
    assert res["interval"] == 1 and res["ease"] < 2.5
    updated = [x for x in c.get(f"/api/decks/{deck_id}").json()["cards"] if x["id"] == card["id"]][0]
    assert updated["interval"] == 1 and updated["reps"] == 0
    # card no longer due today
    assert card["id"] not in [x["id"] for x in c.get(f"/api/decks/{deck_id}/review").json()["due"]]
    print("SM-2 review OK")


def test_quiz():
    deck = c.get("/api/decks").json()[-1]
    deck_id = deck["id"]
    mcqs = c.get(f"/api/decks/{deck_id}").json()["mcqs"]
    m = mcqs[0]
    correct = c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": m["id"], "choice": m["correct_index"]}).json()
    assert correct["correct"] is True and correct["explanation"]
    wrong = c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": m["id"], "choice": (m["correct_index"] + 1) % len(m["options"])}).json()
    assert wrong["correct"] is False and wrong["correct_index"] == m["correct_index"]
    assert c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": 999999, "choice": 0}).status_code == 404
    assert c.post(f"/api/decks/{deck_id}/quiz/answer", json={}).status_code == 400
    # string choice is coerced to int (was silently marked wrong before)
    assert c.post(f"/api/decks/{deck_id}/quiz/answer", json={"mcq_id": m["id"], "choice": str(m["correct_index"])}).json()["correct"] is True
    stats = [x for x in c.get(f"/api/decks/{deck_id}").json()["mcqs"] if x["id"] == m["id"]][0]
    assert stats["answered"] == 3 and stats["correct"] == 2
    assert c.delete(f"/api/decks/{deck_id}/mcqs/{m['id']}").status_code == 200
    print("quiz + stats OK")


def test_quiz_missing_deck_404():
    # was an unhandled 500 before the fix
    assert c.post("/api/decks/999999/quiz/answer", json={"mcq_id": 1, "choice": 0}).status_code == 404
    print("quiz missing-deck 404 OK")


def test_card_and_mcq_validation():
    from pydantic import ValidationError

    from app.schemas import Flashcard, MCQ

    for bad in (
        {"type": "Basic", "front": "", "back": ""},
        {"type": "Basic", "front": "Q", "back": "  "},
        {"type": "Cloze", "text": ""},
    ):
        try:
            Flashcard.model_validate(bad)
        except ValidationError:
            pass
        else:
            raise AssertionError(f"empty card accepted: {bad}")
    for bad in (
        {"question": "Q", "options": ["a"], "correct_index": 0},
        {"question": "Q", "options": ["a", "b"], "correct_index": 5},
    ):
        try:
            MCQ.model_validate(bad)
        except ValidationError:
            pass
        else:
            raise AssertionError(f"invalid mcq accepted: {bad}")
    # empty update rejected via API (was silently saved before)
    import app.llm as app_llm
    from types import SimpleNamespace

    orig = app_llm.get_settings
    app_llm.get_settings = lambda: SimpleNamespace(mock_llm=True, openai_api_key="")
    try:
        deck = c.post("/api/decks", json={"name": "Val Deck"}).json()
        deck_id = deck["id"]
        r = c.post(f"/api/decks/{deck_id}/generate", json={
            "source_text": "x", "flashcards": True, "mcqs": False, "num_cards": 1, "num_mcqs": 1, "tags": []})
        for _ in range(60):
            job = c.get(f"/api/jobs/{r.json()['job_id']}").json()
            if job["status"] in ("done", "error"):
                break
            time.sleep(1)
        assert job["status"] == "done", job
        card = c.get(f"/api/decks/{deck_id}").json()["cards"][0]
        assert c.patch(f"/api/decks/{deck_id}/cards/{card['id']}", json={"front": ""}).status_code == 422
        c.delete(f"/api/decks/{deck_id}")
    finally:
        app_llm.get_settings = orig
    print("card + MCQ validation OK")


def test_anki_deck_id_stable():
    """Deck id must be deterministic across processes, so re-imports don't
    create duplicate Anki decks (built-in hash() is per-process seeded)."""
    import subprocess
    import sys

    code = (
        "from app.anki_export import _deck_id; print(_deck_id('Biology 101'))"
    )
    ids = set()
    for _ in range(3):
        out = subprocess.run([sys.executable, "-c", code], capture_output=True, text=True)
        assert out.returncode == 0, out.stderr
        ids.add(out.stdout.strip())
    assert len(ids) == 1, f"deck id not stable across runs: {ids}"
    assert int(ids.pop()) >= 0
    print("anki deck id stability OK")


def test_export():
    deck = c.get("/api/decks").json()[-1]
    deck_id = deck["id"]
    r = c.get(f"/api/decks/{deck_id}/export")
    assert r.status_code == 200 and r.content[:4] == b"PK\x03\x04"
    assert "filename=" in r.headers["content-disposition"]
    empty = c.post("/api/decks", json={"name": "Empty Deck"}).json()
    assert c.get(f"/api/decks/{empty['id']}/export").status_code == 400
    print("export OK")


def test_upload():
    txt = c.post("/api/upload", files={"file": ("notes.txt", b"Hello mitochondria world", "text/plain")}).json()
    assert txt["text"] == "Hello mitochondria world"
    bad = c.post("/api/upload", files={"file": ("x.bin", b"\x00\xff\xfe\x01garbage", "application/octet-stream")})
    assert bad.status_code == 400
    corrupt_pdf = c.post("/api/upload", files={"file": ("x.pdf", b"not a real pdf at all", "application/pdf")})
    assert corrupt_pdf.status_code == 400
    print("upload handling OK")


def test_ocr_image():
    import io

    from PIL import Image, ImageDraw, ImageFont

    img = Image.new("RGB", (1200, 300), "white")
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 64)
    except Exception:
        font = ImageFont.load_default()
    d.text((60, 100), "MITOCHONDRIA ATP SYNTHASE", fill="black", font=font)
    buf = io.BytesIO()
    img.save(buf, "PNG")
    r = c.post("/api/upload", files={"file": ("slide.png", buf.getvalue(), "image/png")})
    assert r.status_code == 200, r.text
    low = r.json()["text"].lower()
    assert "mitochondria" in low and "atp" in low, low
    assert r.json()["ocr"] is True
    print("OCR image OK")


def test_ocr_scanned_pdf():
    import io

    from PIL import Image, ImageDraw, ImageFont

    img = Image.new("RGB", (1200, 400), "white")
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 56)
    except Exception:
        font = ImageFont.load_default()
    d.text((60, 80), "PROTEIN TRANSPORT", fill="black", font=font)
    d.text((60, 200), "endoplasmic reticulum", fill="black", font=font)
    pdf_buf = io.BytesIO()
    img.save(pdf_buf, "PDF", resolution=150)
    pdf_bytes = pdf_buf.getvalue()
    assert b"%PDF" in pdf_bytes

    no_ocr = c.post("/api/upload", files={"file": ("scan.pdf", pdf_bytes, "application/pdf")}, data={"use_ocr": "false"})
    assert no_ocr.status_code == 400

    with_ocr = c.post("/api/upload", files={"file": ("scan.pdf", pdf_bytes, "application/pdf")}, data={"use_ocr": "true"})
    assert with_ocr.status_code == 200, with_ocr.text
    low = with_ocr.json()["text"].lower()
    assert "transport" in low and "endoplasmic" in low, low
    assert "ocr" in low
    print("OCR scanned PDF OK")


def test_llm_cache_and_rate_limit_resume():
    """429 pauses the job and auto-resumes after the reset; the response
    cache makes identical re-generations free (no new API calls)."""
    import json as _json
    from types import SimpleNamespace

    import app.llm as app_llm

    fake_settings = SimpleNamespace(
        mock_llm=False, openai_api_key="sk-test", openai_base_url="x",
        llm_model="test-model", llm_temperature=0.2,
    )
    orig_get_settings = app_llm.get_settings
    orig_chat = app_llm._chat
    orig_pad = app_llm.RETRY_PAD_SECONDS
    app_llm.get_settings = lambda: fake_settings
    app_llm.RETRY_PAD_SECONDS = 0.5
    app_llm.db.llm_cache_clear()  # make the test idempotent across runs

    payload = _json.dumps([
        {"type": "Basic", "front": "Q: X", "back": "A", "tags": [], "source": ""},
        {"type": "Basic", "front": "Q: Y", "back": "B", "tags": [], "source": ""},
    ])
    calls = {"n": 0}

    def fake_chat(system, user):
        calls["n"] += 1
        if calls["n"] == 1:
            raise app_llm.RateLimitError(int((time.time() + 3) * 1000))
        return payload

    app_llm._chat = fake_chat

    try:
        source = "Cache me: mitochondria produce ATP."
        deck1 = c.post("/api/decks", json={"name": "RL Deck 1"}).json()
        r = c.post(f"/api/decks/{deck1['id']}/generate", json={
            "source_text": source, "num_cards": 2, "flashcards": True,
            "num_mcqs": 1, "mcqs": False, "tags": []})
        job_id = r.json()["job_id"]
        saw_paused = False
        job = None
        for _ in range(240):
            time.sleep(0.1)
            job = c.get(f"/api/jobs/{job_id}").json()
            if job["status"] == "paused":
                saw_paused = True
                assert job["reset_ms"]
            if job["status"] in ("done", "error"):
                break
        assert job["status"] == "done", job
        assert saw_paused, "job never showed paused"
        assert job["cards"] == 2
        assert calls["n"] == 2, f"expected 1 raise + 1 retry, got {calls['n']}"

        deck2 = c.post("/api/decks", json={"name": "RL Deck 2"}).json()
        r = c.post(f"/api/decks/{deck2['id']}/generate", json={
            "source_text": source, "num_cards": 2, "flashcards": True,
            "num_mcqs": 1, "mcqs": False, "tags": []})
        job2 = None
        for _ in range(60):
            time.sleep(0.25)
            job2 = c.get(f"/api/jobs/{r.json()['job_id']}").json()
            if job2["status"] in ("done", "error"):
                break
        assert job2["status"] == "done", job2
        assert job2["cards"] == 2
        assert calls["n"] == 2, f"cache failed: {calls['n']} API calls"
        print("llm cache + 429 pause/resume OK")
    finally:
        app_llm.get_settings = orig_get_settings
        app_llm._chat = orig_chat
        app_llm.RETRY_PAD_SECONDS = orig_pad


def test_models_and_settings():
    """Model list always has the cloud entry; settings PATCH updates .env."""
    import app.main as app_main

    from app.config import get_settings

    models = c.get("/api/models").json()
    assert models and models[0]["id"] == "configured"
    assert all(isinstance(m["label"], str) and m["model"] for m in models)

    env_path = app_main._env_path()
    backup = env_path.read_text(encoding="utf-8") if env_path.exists() else ""
    try:
        r = c.patch("/api/settings", json={"base_url": "http://127.0.0.1:11434/v1", "model": "gemma3:4b"})
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["model"] == "gemma3:4b" and body["base_url"] == "http://127.0.0.1:11434/v1", body
        content = env_path.read_text(encoding="utf-8")
        assert "OPENAI_BASE_URL=http://127.0.0.1:11434/v1" in content
        assert "LLM_MODEL=gemma3:4b" in content
        r2 = c.patch("/api/settings", json={"model": "only-model-change"})
        assert r2.json()["model"] == "only-model-change"
        assert r2.json()["base_url"] == "http://127.0.0.1:11434/v1"
    finally:
        env_path.write_text(backup, encoding="utf-8")
        get_settings.cache_clear()
    print("models probe + settings patch OK")


def test_chunked_generation_whole_book():
    """Sources larger than one prompt chunk get mined chunk by chunk, so a
    whole book's content is covered; identical re-generation is cached."""
    import json as _json
    from types import SimpleNamespace

    import app.llm as app_llm

    fake_settings = SimpleNamespace(
        mock_llm=False, openai_api_key="sk-test", openai_base_url="x",
        llm_model="test-model", llm_temperature=0.2,
    )
    orig_get_settings, orig_chat = app_llm.get_settings, app_llm._chat
    app_llm.get_settings = lambda: fake_settings
    calls = {"n": 0}

    def fake_chat(system, user):
        calls["n"] += 1
        return _json.dumps([{
            "type": "Basic", "front": f"Book card {calls['n']}", "back": "b",
            "tags": [], "source": "",
        }])

    app_llm._chat = fake_chat
    try:
        book = "Chapter One. " * 400 + " ".join(f"Sentence {i} about mitochondria." for i in range(4000))
        assert len(book) > 100000
        deck = c.post("/api/decks", json={"name": "Book Deck"}).json()
        r = c.post(f"/api/decks/{deck['id']}/generate", json={
            "source_text": book, "num_cards": 3, "flashcards": True,
            "num_mcqs": 1, "mcqs": False, "tags": []})
        assert r.status_code == 200, r.text
        job = None
        for _ in range(120):
            time.sleep(0.25)
            job = c.get(f"/api/jobs/{r.json()['job_id']}").json()
            if job["status"] in ("done", "error"):
                break
        assert job["status"] == "done", job
        assert job["cards"] == 3, job
        assert calls["n"] == 3, f"expected one call per chunk, got {calls['n']}"
        deck_data = c.get(f"/api/decks/{deck['id']}").json()
        fronts = [cd["front"] for cd in deck_data["cards"]]
        assert fronts == ["Book card 1", "Book card 2", "Book card 3"], fronts

        deck2 = c.post("/api/decks", json={"name": "Book Deck 2"}).json()
        r = c.post(f"/api/decks/{deck2['id']}/generate", json={
            "source_text": book, "num_cards": 3, "flashcards": True,
            "num_mcqs": 1, "mcqs": False, "tags": []})
        for _ in range(120):
            time.sleep(0.25)
            job2 = c.get(f"/api/jobs/{r.json()['job_id']}").json()
            if job2["status"] in ("done", "error"):
                break
        assert job2["status"] == "done", job2
        assert job2["cards"] == 3
        assert calls["n"] == 3, f"cache failed: {calls['n']} calls"
        print("chunked whole-book generation OK")
    finally:
        app_llm.get_settings, app_llm._chat = orig_get_settings, orig_chat


def test_whole_book_pdf_ingest():
    """A 250-page PDF is fully ingested (past the old 200-page cap)."""
    from reportlab.lib.pagesizes import letter
    from reportlab.pdfgen import canvas

    buf = io.BytesIO()
    cv = canvas.Canvas(buf, pagesize=letter)
    for i in range(1, 251):
        cv.drawString(72, 720, f"Page {i} of the test book")
        cv.showPage()
    cv.save()
    r = c.post("/api/upload", files={"file": ("book.pdf", buf.getvalue(), "application/pdf")}, data={"use_ocr": "false"})
    assert r.status_code == 200, r.text
    text = r.json()["text"]
    assert "[Page 250]" in text and "[Page 201]" in text
    assert text.count("[Page ") >= 200
    print("whole-book PDF ingest OK")


def test_ocr_endpoint():
    """POST /api/ocr reads images server-side so the Android app can hand scans
    off instead of burning its own cloud OCR quota."""
    from PIL import Image, ImageDraw, ImageFont

    img = Image.new("RGB", (1200, 300), "white")
    d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("C:/Windows/Fonts/arialbd.ttf", 64)
    except Exception:
        font = ImageFont.load_default()
    d.text((60, 100), "NUCLEUS CELL DIVISION", fill="black", font=font)
    buf = io.BytesIO()
    img.save(buf, "PNG")

    r = c.post("/api/ocr", files={"files": ("slide.png", buf.getvalue(), "image/png")})
    assert r.status_code == 200, r.text
    body = r.json()
    low = body["text"].lower()
    assert "nucleus" in low and "division" in low, low
    assert body["ocr"] is True and body["images"] == 1

    two = c.post("/api/ocr", files=[
        ("files", ("a.png", buf.getvalue(), "image/png")),
        ("files", ("b.png", buf.getvalue(), "image/png")),
    ])
    assert two.status_code == 200, two.text
    assert two.json()["images"] == 2

    bad = c.post("/api/ocr", files={"files": ("x.png", b"not an image", "image/png")})
    assert bad.status_code == 400, bad.text

    assert c.post("/api/ocr").status_code in (400, 422)
    print("POST /api/ocr OK")


def test_generate_endpoint():
    """POST /api/generate is the deck-less synchronous path the Android app
    uses for whole-book sources (it falls back on-device when this fails)."""
    r = c.post("/api/generate", json={
        "source_text": "Mitochondria produce ATP during cellular respiration.",
        "num_cards": 3, "num_mcqs": 2, "flashcards": True, "mcqs": True,
        "tags": ["bio"],
    })
    assert r.status_code == 200, r.text
    body = r.json()
    assert 1 <= len(body["flashcards"]) <= 3, body
    assert 1 <= len(body["mcqs"]) <= 2, body
    assert all("bio" in f["tags"] for f in body["flashcards"]), body
    assert body["flashcards"][0]["front"], body

    cards_only = c.post("/api/generate", json={
        "source_text": "Source material for cards only.", "num_cards": 2,
        "flashcards": True, "num_mcqs": 1, "mcqs": False, "tags": []})
    assert cards_only.status_code == 200, cards_only.text
    assert cards_only.json()["mcqs"] == []
    assert len(cards_only.json()["flashcards"]) == 2

    assert c.post("/api/generate", json={"source_text": "   "}).status_code == 400
    nothing = c.post("/api/generate", json={
        "source_text": "hello", "flashcards": False, "mcqs": False})
    assert nothing.status_code == 400, nothing.text
    print("POST /api/generate OK")


def test_junk_page_pdf_is_not_trusted():
    """A page whose native extraction is only a page number is treated as a
    scan, not handed to the LLM as if it were real content."""
    from reportlab.lib.pagesizes import letter
    from reportlab.pdfgen import canvas

    buf = io.BytesIO()
    cv = canvas.Canvas(buf, pagesize=letter)
    cv.drawString(72, 700, "Photosynthesis converts light energy into chemical energy stored in glucose.")
    cv.showPage()
    cv.drawString(72, 720, "7")  # page number only — the scanned-page signature
    cv.showPage()
    cv.save()

    r = c.post("/api/upload", files={"file": ("mixed.pdf", buf.getvalue(), "application/pdf")},
               data={"use_ocr": "false"})
    assert r.status_code == 200, r.text
    text = r.json()["text"]
    assert "[Page 1]" in text and "Photosynthesis" in text, text
    assert "[Page 2]" not in text, "junk page leaked into the source text"
    print("junk-page routing OK")


def test_dedupe_text():
    from app.ingest import _dedupe_text, _is_junk_text

    noisy = "\n".join(["Running Header"] * 10) + "\nReal content here"
    out = _dedupe_text(noisy)
    assert out.lower().count("running header") <= 3, out
    assert "Real content here" in out

    assert _dedupe_text("only line") == "only line"
    assert _dedupe_text("") == ""

    assert _is_junk_text("") is True
    assert _is_junk_text("7") is True
    assert _is_junk_text("Chapter Three") is True
    assert _is_junk_text("Page 7 of the test book") is False
    print("text dedupe + junk detection OK")


def test_study_request():
    """POST /api/study-request: prompt + class/school/city/country -> fetch
    -> new deck -> generation job, with a guarded 502 when fetch yields nothing."""
    import app.textbook as textbook_mod

    original = textbook_mod.fetch_textbook
    textbook_mod.fetch_textbook = lambda *a, **k: (
        "Mitochondria produce ATP. Chloroplasts run photosynthesis. "
        "Ribosomes build proteins from amino acids.",
        "mock",
    )
    try:
        r = c.post("/api/study-request", json={
            "prompt": "Cell organelles and their functions",
            "class_name": "Grade 10", "school": "City High",
            "city": "Karachi", "country": "Pakistan",
            "deck_name": "Study Deck",
            "num_cards": 3, "num_mcqs": 1,
            "tags": ["Bio"],
        })
        assert r.status_code == 201, r.text
        body = r.json()
        assert body["provider"] == "mock" and body["chars"] > 0
        assert body["deck_id"] and body["job_id"]

        # validation
        assert c.post("/api/study-request", json={"prompt": "x"}).status_code == 422
        assert c.post("/api/study-request", json={"prompt": "   "}).status_code == 400
        assert c.post("/api/study-request", json={"prompt": "valid", "num_cards": 0}).status_code == 422
        assert c.post("/api/study-request", json={"prompt": "valid", "num_mcqs": 101}).status_code == 422

        # a fetch that returns nothing must not create a deck: guarded 502
        textbook_mod.fetch_textbook = lambda *a, **k: ("", "prompt")
        before = len(c.get("/api/decks").json())
        assert c.post("/api/study-request", json={"prompt": "valid prompt"}).status_code == 502
        assert len(c.get("/api/decks").json()) == before

        # the real job completes and the deck has the requested content
        job = None
        for _ in range(60):
            job = c.get(f"/api/jobs/{body['job_id']}").json()
            if job["status"] in ("done", "error"):
                break
            time.sleep(1)
        assert job["status"] == "done", job
        deck = c.get(f"/api/decks/{body['deck_id']}").json()
        assert len(deck["cards"]) == 3 and len(deck["mcqs"]) == 1, deck
        assert all("Grade 10" in card["tags"] for card in deck["cards"]), deck["cards"]
    finally:
        textbook_mod.fetch_textbook = original
    print("study-request OK")


def test_gemini_text_error_paths():
    """Retried/exhausted HTTP errors in _gemini_text must degrade to ""
    instead of raising UnboundLocalError on the never-assigned resp."""
    import urllib.error
    from types import SimpleNamespace

    import app.textbook as textbook_mod

    orig_post, orig_sleep = textbook_mod._post_json, textbook_mod.time.sleep
    settings = SimpleNamespace(gemini_api_key="k", gemini_model="gemini-test")
    try:
        def raise_400(*a, **k):
            raise urllib.error.HTTPError("http://x", 400, "Bad", {}, None)

        textbook_mod._post_json = raise_400
        assert textbook_mod._gemini_text(settings, "hi", 10, 1) == ""

        textbook_mod.time.sleep = lambda *_: None

        def raise_429(*a, **k):
            raise urllib.error.HTTPError("http://x", 429, "Slow down", {}, None)

        textbook_mod._post_json = raise_429
        assert textbook_mod._gemini_text(settings, "hi", 10, 1) == ""
    finally:
        textbook_mod._post_json = orig_post
        textbook_mod.time.sleep = orig_sleep
    print("gemini text error paths OK")


def test_chat_model_fallback():
    """A transient failure on the primary model walks the Gemini fallback
    list instead of stalling the job in a 60s pause loop."""
    from types import SimpleNamespace

    import httpx
    from openai import APIConnectionError

    import app.llm as app_llm

    fake_settings = SimpleNamespace(
        mock_llm=False, openai_api_key="k",
        openai_base_url="https://generativelanguage.googleapis.com/v1beta/openai",
        llm_model="gemini-3.5-flash", llm_temperature=0.4,
    )
    calls = []

    class FakeCompletions:
        def create(self, **kw):
            calls.append(kw["model"])
            if kw["model"] == "gemini-3.5-flash":
                raise APIConnectionError(request=httpx.Request("POST", "http://x"))
            return SimpleNamespace(choices=[SimpleNamespace(
                finish_reason="stop",
                message=SimpleNamespace(content='[{"front": "q", "back": "a"}]'),
            )])

    fake_client = SimpleNamespace(chat=SimpleNamespace(completions=FakeCompletions()))
    orig_settings, orig_client = app_llm.get_settings, app_llm._client
    app_llm.get_settings = lambda: fake_settings
    app_llm._client = lambda: fake_client
    try:
        out = app_llm._chat("system", "user")
        assert out.startswith("["), out
        assert calls == ["gemini-3.5-flash", "gemini-3.5-flash-lite"], calls

        # non-Gemini endpoints keep today's single-model behaviour
        fake_settings.openai_base_url = "https://api.openai.com/v1"
        calls.clear()
        try:
            app_llm._chat("system", "user")
        except Exception:
            pass
        assert calls == ["gemini-3.5-flash"], calls
    finally:
        app_llm.get_settings = orig_settings
        app_llm._client = orig_client
    print("chat model fallback OK")


def test_cleanup():
    decks = c.get("/api/decks").json()
    for d in decks:
        if d["name"] in ("Suite Deck", "Job Deck", "Empty Deck", "RL Deck 1", "RL Deck 2", "Book Deck", "Book Deck 2", "Study Deck"):
            c.delete(f"/api/decks/{d['id']}")
    remaining = c.get("/api/decks").json()
    assert all(d["name"] not in ("Suite Deck", "Job Deck", "Empty Deck", "RL Deck 1", "RL Deck 2", "Book Deck", "Book Deck 2", "Study Deck") for d in remaining)
    print("cleanup OK")


if __name__ == "__main__":
    test_landing_and_assets()
    test_desktop_download()
    test_settings_and_decks()
    test_generation_job_and_persistence()
    test_cards_crud()
    test_sm2_review()
    test_quiz()
    test_quiz_missing_deck_404()
    test_card_and_mcq_validation()
    test_anki_deck_id_stable()
    test_export()
    test_upload()
    test_ocr_image()
    test_ocr_scanned_pdf()
    test_ocr_endpoint()
    test_generate_endpoint()
    test_junk_page_pdf_is_not_trusted()
    test_dedupe_text()
    test_study_request()
    test_gemini_text_error_paths()
    test_chat_model_fallback()
    test_llm_cache_and_rate_limit_resume()
    test_models_and_settings()
    test_chunked_generation_whole_book()
    test_whole_book_pdf_ingest()
    test_cleanup()
    print("ALL APP TESTS PASSED")
