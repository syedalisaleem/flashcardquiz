package com.syedali.flashquiz.engine

data class SM2Result(
    val easeFactor: Double,
    val interval: Int,
    val repetitions: Int,
    val nextReview: Long
)

object SpacedRepetitionEngine {
    fun calculate(
        currentEaseFactor: Double,
        currentInterval: Int,
        currentRepetitions: Int,
        quality: Int
    ): SM2Result {
        var ef = currentEaseFactor
        var interval = currentInterval
        var rep = currentRepetitions

        if (quality >= 3) {
            when (rep) {
                0 -> interval = 1
                1 -> interval = 6
                else -> interval = (interval * ef).toInt()
            }
            rep++
        } else {
            rep = 0
            interval = 1
        }

        ef = ef + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
        if (ef < 1.3) ef = 1.3

        val now = System.currentTimeMillis()
        val nextReview = now + interval * 24L * 60 * 60 * 1000

        return SM2Result(ef, interval, rep, nextReview)
    }
}
