"""SQLite persistence: decks, flashcards, MCQs, SM-2 scheduling, quiz stats."""

from __future__ import annotations

import json
import os
import sqlite3
import sys
import threading
from datetime import date, timedelta
from pathlib import Path

from . import sm2
from .schemas import Flashcard, FlashcardUpdate, MCQ, SM2Card


def _data_dir() -> Path:
    """SQLite lives next to the code in dev; in %LOCALAPPDATA% when frozen (.exe)."""
    if getattr(sys, "frozen", False):
        data = Path(os.environ.get("LOCALAPPDATA") or Path.home()) / "FlashcardQuiz"
        data.mkdir(parents=True, exist_ok=True)
        return data
    return Path(__file__).resolve().parent


DB_PATH = _data_dir() / "data.db"

SCHEMA = """
CREATE TABLE IF NOT EXISTS decks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS flashcards (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    type TEXT NOT NULL DEFAULT 'Basic',
    front TEXT NOT NULL DEFAULT '',
    back TEXT NOT NULL DEFAULT '',
    text TEXT NOT NULL DEFAULT '',
    tags TEXT NOT NULL DEFAULT '[]',
    source TEXT NOT NULL DEFAULT '',
    ease REAL NOT NULL DEFAULT 2.5,
    interval INTEGER NOT NULL DEFAULT 0,
    reps INTEGER NOT NULL DEFAULT 0,
    due TEXT NOT NULL DEFAULT '1970-01-01'
);
CREATE TABLE IF NOT EXISTS mcqs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    deck_id INTEGER NOT NULL REFERENCES decks(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    options TEXT NOT NULL DEFAULT '[]',
    correct_index INTEGER NOT NULL DEFAULT 0,
    explanation TEXT NOT NULL DEFAULT '',
    distractor_explanations TEXT NOT NULL DEFAULT '{}',
    tags TEXT NOT NULL DEFAULT '[]',
    answered INTEGER NOT NULL DEFAULT 0,
    correct INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_flashcards_deck ON flashcards(deck_id);
CREATE INDEX IF NOT EXISTS idx_mcqs_deck ON mcqs(deck_id);
CREATE TABLE IF NOT EXISTS llm_cache (
    cache_key TEXT PRIMARY KEY,
    response TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
"""

_conn: sqlite3.Connection | None = None
_lock = threading.Lock()


def _db() -> sqlite3.Connection:
    global _conn
    if _conn is None:
        _conn = sqlite3.connect(DB_PATH, check_same_thread=False)
        _conn.row_factory = sqlite3.Row
        _conn.execute("PRAGMA foreign_keys = ON")
    return _conn


def _query(sql: str, params: tuple = ()) -> list[sqlite3.Row]:
    """Run a SELECT while holding the connection lock."""
    with _lock:
        return _db().execute(sql, params).fetchall()


def _execute(sql: str, params: tuple = ()) -> sqlite3.Cursor:
    """Run a write and commit while holding the connection lock."""
    with _lock:
        cur = _db().execute(sql, params)
        _db().commit()
        return cur


def init_db() -> None:
    _db().executescript(SCHEMA)
    if not _query("SELECT id FROM decks"):
        _execute(
            "INSERT INTO decks (name, created_at) VALUES (?, ?)",
            ("Lecture 01", date.today().isoformat()),
        )


# ---------------------------------------------------------------- llm cache

def llm_cache_get(key: str) -> str | None:
    rows = _query("SELECT response FROM llm_cache WHERE cache_key=?", (key,))
    return rows[0]["response"] if rows else None


def llm_cache_put(key: str, response: str) -> None:
    _execute(
        "INSERT INTO llm_cache (cache_key, response) VALUES (?, ?) "
        "ON CONFLICT(cache_key) DO UPDATE SET response=excluded.response",
        (key, response),
    )


def llm_cache_clear() -> None:
    _execute("DELETE FROM llm_cache")


# ---------------------------------------------------------------- decks

def list_decks() -> list[dict]:
    today = date.today().isoformat()
    rows = _query(
        """
        SELECT d.id, d.name, d.created_at,
               (SELECT COUNT(*) FROM flashcards f WHERE f.deck_id = d.id) AS cards,
               (SELECT COUNT(*) FROM mcqs m WHERE m.deck_id = d.id) AS mcqs,
               (SELECT COUNT(*) FROM flashcards f WHERE f.deck_id = d.id AND f.due <= ?) AS due
        FROM decks d ORDER BY d.id
        """,
        (today,),
    )
    return [dict(r) for r in rows]


def create_deck(name: str) -> dict:
    cur = _execute(
        "INSERT INTO decks (name, created_at) VALUES (?, ?)",
        (name, date.today().isoformat()),
    )
    return {"id": cur.lastrowid, "name": name, "created_at": date.today().isoformat(), "cards": 0, "mcqs": 0, "due": 0}


def delete_deck(deck_id: int) -> None:
    _execute("DELETE FROM decks WHERE id=?", (deck_id,))


def deck_exists(deck_id: int) -> bool:
    return bool(_query("SELECT id FROM decks WHERE id=?", (deck_id,)))


# ---------------------------------------------------------------- flashcards

def _card_row_to_dict(r: sqlite3.Row) -> dict:
    return {
        "id": r["id"], "deck_id": r["deck_id"], "type": r["type"], "front": r["front"],
        "back": r["back"], "text": r["text"], "tags": json.loads(r["tags"]),
        "source": r["source"], "ease": r["ease"], "interval": r["interval"],
        "reps": r["reps"], "due": r["due"],
    }


def _mcq_row_to_dict(r: sqlite3.Row) -> dict:
    return {
        "id": r["id"], "deck_id": r["deck_id"], "question": r["question"],
        "options": json.loads(r["options"]), "correct_index": r["correct_index"],
        "explanation": r["explanation"],
        "distractor_explanations": json.loads(r["distractor_explanations"]),
        "tags": json.loads(r["tags"]), "answered": r["answered"], "correct": r["correct"],
    }


def get_deck(deck_id: int) -> dict:
    deck = _query("SELECT * FROM decks WHERE id=?", (deck_id,))
    if not deck:
        raise ValueError("Deck not found")
    cards = _query("SELECT * FROM flashcards WHERE deck_id=? ORDER BY id", (deck_id,))
    mcqs = _query("SELECT * FROM mcqs WHERE deck_id=? ORDER BY id", (deck_id,))
    r = deck[0]
    return {
        "id": r["id"], "name": r["name"], "created_at": r["created_at"],
        "cards": [_card_row_to_dict(c) for c in cards],
        "mcqs": [_mcq_row_to_dict(m) for m in mcqs],
    }


def add_flashcards(deck_id: int, cards: list[Flashcard]) -> None:
    if not deck_exists(deck_id):
        raise ValueError("Deck was deleted")
    with _lock:
        _db().executemany(
            "INSERT INTO flashcards (deck_id, type, front, back, text, tags, source) VALUES (?,?,?,?,?,?,?)",
            [(deck_id, c.type, c.front, c.back, c.text, json.dumps(c.tags), c.source) for c in cards],
        )
        _db().commit()


def get_card(deck_id: int, card_id: int) -> dict:
    rows = _query("SELECT * FROM flashcards WHERE id=? AND deck_id=?", (card_id, deck_id))
    if not rows:
        raise ValueError("Card not found")
    return _card_row_to_dict(rows[0])


def update_flashcard(deck_id: int, card_id: int, data: FlashcardUpdate) -> dict:
    fields = data.model_dump(exclude_none=True)
    if "tags" in fields:
        fields["tags"] = json.dumps(fields["tags"])
    if not fields:
        return get_card(deck_id, card_id)
    sets = ", ".join(f"{k}=?" for k in fields)
    cur = _execute(
        f"UPDATE flashcards SET {sets} WHERE id=? AND deck_id=?",
        (*fields.values(), card_id, deck_id),
    )
    if cur.rowcount == 0:
        raise ValueError("Card not found")
    return get_card(deck_id, card_id)


def delete_flashcard(deck_id: int, card_id: int) -> None:
    cur = _execute("DELETE FROM flashcards WHERE id=? AND deck_id=?", (card_id, deck_id))
    if cur.rowcount == 0:
        raise ValueError("Card not found")


def due_cards(deck_id: int) -> list[dict]:
    rows = _query(
        "SELECT * FROM flashcards WHERE deck_id=? AND due<=? ORDER BY due, id",
        (deck_id, date.today().isoformat()),
    )
    return [_card_row_to_dict(r) for r in rows]


def apply_review(deck_id: int, card_id: int, quality: int) -> dict:
    card = get_card(deck_id, card_id)
    sm = sm2.review(
        SM2Card(front=card["front"], back=card["back"],
                ease=card["ease"], interval=card["interval"], reps=card["reps"]),
        quality,
    )
    due_date = (date.today() + timedelta(days=sm.interval)).isoformat()
    _execute(
        "UPDATE flashcards SET ease=?, interval=?, reps=?, due=? WHERE id=? AND deck_id=?",
        (sm.ease, sm.interval, sm.reps, due_date, card_id, deck_id),
    )
    return {"card_id": card_id, "ease": sm.ease, "interval": sm.interval, "reps": sm.reps, "due": due_date}


# ---------------------------------------------------------------- mcqs

def add_mcqs(deck_id: int, mcqs: list[MCQ]) -> None:
    if not deck_exists(deck_id):
        raise ValueError("Deck was deleted")
    with _lock:
        _db().executemany(
            "INSERT INTO mcqs (deck_id, question, options, correct_index, explanation, distractor_explanations, tags) VALUES (?,?,?,?,?,?,?)",
            [
                (deck_id, m.question, json.dumps(m.options), m.correct_index, m.explanation,
                 json.dumps(m.distractor_explanations), json.dumps(m.tags))
                for m in mcqs
            ],
        )
        _db().commit()


def get_mcq(deck_id: int, mcq_id: int) -> dict | None:
    rows = _query("SELECT * FROM mcqs WHERE id=? AND deck_id=?", (mcq_id, deck_id))
    return _mcq_row_to_dict(rows[0]) if rows else None


def delete_mcq(deck_id: int, mcq_id: int) -> None:
    cur = _execute("DELETE FROM mcqs WHERE id=? AND deck_id=?", (mcq_id, deck_id))
    if cur.rowcount == 0:
        raise ValueError("Question not found")


def record_answer(deck_id: int, mcq_id: int, correct: bool) -> None:
    _execute(
        "UPDATE mcqs SET answered=answered+1, correct=correct+? WHERE id=? AND deck_id=?",
        (1 if correct else 0, mcq_id, deck_id),
    )
