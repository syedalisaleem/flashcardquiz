package com.syedali.flashquiz.data.mapper

import com.syedali.flashquiz.data.local.entity.DeckEntity
import com.syedali.flashquiz.data.local.entity.FlashcardEntity
import com.syedali.flashquiz.data.local.entity.McqEntity
import com.syedali.flashquiz.model.Deck
import com.syedali.flashquiz.model.Flashcard
import com.syedali.flashquiz.model.MCQ

fun DeckEntity.toDomain(): Deck = Deck(
    id = id,
    name = name,
    tags = tags,
    createdAt = createdAt
)

fun Deck.toEntity(): DeckEntity = DeckEntity(
    id = id,
    name = name,
    tags = tags,
    createdAt = createdAt
)

fun FlashcardEntity.toDomain(): Flashcard = Flashcard(
    id = id,
    deckId = deckId,
    type = type,
    front = front,
    back = back,
    text = text,
    tags = tags,
    source = source,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    nextReview = nextReview,
    lastReview = lastReview
)

fun Flashcard.toEntity(): FlashcardEntity = FlashcardEntity(
    id = id,
    deckId = deckId,
    type = type,
    front = front,
    back = back,
    text = text,
    tags = tags,
    source = source,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    nextReview = nextReview,
    lastReview = lastReview
)

fun McqEntity.toDomain(): MCQ = MCQ(
    id = id,
    deckId = deckId,
    question = question,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
    distractorExplanations = distractorExplanations,
    tags = tags,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    nextReview = nextReview,
    lastReview = lastReview
)

fun MCQ.toEntity(): McqEntity = McqEntity(
    id = id,
    deckId = deckId,
    question = question,
    options = options,
    correctIndex = correctIndex,
    explanation = explanation,
    distractorExplanations = distractorExplanations,
    tags = tags,
    easeFactor = easeFactor,
    interval = interval,
    repetitions = repetitions,
    nextReview = nextReview,
    lastReview = lastReview
)
