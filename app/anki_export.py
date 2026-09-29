"""Anki .apkg export via genanki."""

from __future__ import annotations

import hashlib
import io
import re

import genanki

from .schemas import Flashcard

BASIC_MODEL = genanki.Model(
    1607392319,
    "Basic AI Model",
    fields=[{"name": "Question"}, {"name": "Answer"}],
    templates=[
        {
            "name": "Card 1",
            "qfmt": '<div class="card">{{Question}}</div>',
            "afmt": '<div class="card">{{FrontSide}}<hr id="answer">{{Answer}}</div>',
        }
    ],
    css=".card { font-family: arial; font-size: 20px; text-align: center; color: black; }",
)

CLOZE_MODEL = genanki.Model(
    1607392320,
    "Cloze AI Model",
    model_type=genanki.Model.CLOZE,
    fields=[{"name": "Text"}, {"name": "Extra"}],
    templates=[
        {
            "name": "Cloze",
            "qfmt": "{{cloze:Text}}",
            "afmt": "{{cloze:Text}}<br>{{Extra}}",
        }
    ],
    css=".card { font-family: arial; font-size: 20px; text-align: center; color: black; }",
)

_INVALID_TAG = re.compile(r"[^a-zA-Z0-9_ -]")


def _normalize_tag(tag: str) -> str:
    tag = _INVALID_TAG.sub("", tag.strip())
    return tag.replace(" ", "_") or "generated"


def _deck_id(name: str) -> int:
    """Stable deck id across restarts.

    Python's built-in hash() is seeded per-process, so two runs of the same
    app would produce different deck ids and Anki would treat re-imported
    decks as new duplicates. md5 gives a deterministic id for a given name.
    """
    return int(hashlib.md5(name.encode("utf-8")).hexdigest()[:8], 16)


def build_apkg(
    flashcards: list[Flashcard], deck_name: str = "AI Generated Deck"
) -> bytes:
    deck = genanki.Deck(_deck_id(deck_name), deck_name)

    for card in flashcards:
        if card.type == "Cloze" and card.text:
            note = genanki.Note(
                model=CLOZE_MODEL,
                fields=[card.text, card.source or ""],
                tags=[_normalize_tag(t) for t in card.tags] or ["generated"],
            )
        else:
            source = f'<div class="source">{card.source}</div>' if card.source else ""
            note = genanki.Note(
                model=BASIC_MODEL,
                fields=[card.front, f"{card.back}{source}"],
                tags=[_normalize_tag(t) for t in card.tags] or ["generated"],
            )
        deck.add_note(note)

    buf = io.BytesIO()
    genanki.Package(deck).write_to_file(buf)
    return buf.getvalue()
