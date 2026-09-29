"""SM-2 spaced repetition algorithm (same family Anki uses)."""

from __future__ import annotations

from .schemas import SM2Card


def review(card: SM2Card, quality: int) -> SM2Card:
    """Apply one review to a card. quality: 0 = blackout ... 5 = perfect recall."""
    if quality < 3:  # failed recall -> reset
        card.interval = 1
        card.reps = 0
    else:
        if card.reps == 0:
            card.interval = 1
        elif card.reps == 1:
            card.interval = 6
        else:
            card.interval = round(card.interval * card.ease)
        card.reps += 1

    card.ease = max(1.3, card.ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)))
    card.due = card.interval  # days from today
    return card
