"""Pydantic models shared between API, LLM output and Anki export."""

from __future__ import annotations

from typing import Literal, Optional

from pydantic import BaseModel, Field, field_validator, model_validator


class Flashcard(BaseModel):
    type: Literal["Basic", "Cloze"] = "Basic"
    front: str = ""
    back: str = ""
    text: str = ""  # for Cloze: the sentence with {{c1::...}} markers
    tags: list[str] = Field(default_factory=list)
    source: str = ""  # slide number / timestamp / page ref

    @model_validator(mode="after")
    def _require_content(self) -> "Flashcard":
        """A card must be usable: Basic needs a front and back, Cloze needs text."""
        if self.type == "Cloze":
            if not self.text.strip():
                raise ValueError("Cloze card needs a 'text' field with {{c1::...}} markers")
        elif not (self.front.strip() and self.back.strip()):
            raise ValueError("Basic card needs a non-empty front and back")
        return self


class MCQ(BaseModel):
    question: str
    options: list[str]
    correct_index: int
    explanation: str = ""
    distractor_explanations: dict[str, str] = Field(default_factory=dict)
    tags: list[str] = Field(default_factory=list)

    @field_validator("distractor_explanations", mode="before")
    @classmethod
    def _keep_only_index_keys(cls, v):
        """Tolerate models that merge stray keys (e.g. "tags") into the map."""
        if not isinstance(v, dict):
            return {}
        return {k: str(val) for k, val in v.items() if k.isdigit() and isinstance(val, str)}

    @model_validator(mode="after")
    def _check_options(self) -> "MCQ":
        """A question must have options and a valid answer index."""
        if len(self.options) < 2:
            raise ValueError("A question needs at least 2 options")
        if not 0 <= self.correct_index < len(self.options):
            raise ValueError("correct_index is out of range for the given options")
        return self


class GenerateRequest(BaseModel):
    source_text: str = Field(max_length=500000)
    flashcards: bool = True
    mcqs: bool = True
    num_cards: int = Field(default=15, ge=1, le=200)
    num_mcqs: int = Field(default=5, ge=1, le=100)
    tags: list[str] = Field(default_factory=list)


class GenerationResult(BaseModel):
    flashcards: list[Flashcard] = Field(default_factory=list)
    mcqs: list[MCQ] = Field(default_factory=list)


class FlashcardUpdate(BaseModel):
    front: Optional[str] = None
    back: Optional[str] = None
    text: Optional[str] = None
    tags: Optional[list[str]] = None
    source: Optional[str] = None

    @model_validator(mode="after")
    def _not_empty(self) -> "FlashcardUpdate":
        for field in ("front", "back", "text"):
            value = getattr(self, field)
            if value is not None and not value.strip():
                raise ValueError(f"{field} cannot be empty")
        return self


class ReviewRequest(BaseModel):
    deck_id: int
    card_id: int
    quality: int = Field(ge=0, le=5)  # SM-2 grade 0..5


class SM2Card(BaseModel):
    front: str
    back: str
    ease: float = 2.5
    interval: int = 0
    reps: int = 0
    due: int = 0
