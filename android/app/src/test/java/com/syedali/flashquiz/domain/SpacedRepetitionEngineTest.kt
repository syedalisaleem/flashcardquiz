package com.syedali.flashquiz.domain

import com.google.common.truth.Truth.assertThat
import com.syedali.flashquiz.engine.SpacedRepetitionEngine
import org.junit.Test

class SpacedRepetitionEngineTest {

    @Test
    fun firstPass_quality5_setsIntervalTo1() {
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = 2.5,
            currentInterval = 0,
            currentRepetitions = 0,
            quality = 5
        )
        assertThat(result.interval).isEqualTo(1)
        assertThat(result.repetitions).isEqualTo(1)
        assertThat(result.easeFactor).isGreaterThan(2.5)
        assertThat(result.nextReview).isGreaterThan(0L)
    }

    @Test
    fun secondPass_quality4_setsIntervalTo6() {
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = 2.5,
            currentInterval = 1,
            currentRepetitions = 1,
            quality = 4
        )
        assertThat(result.interval).isEqualTo(6)
        assertThat(result.repetitions).isEqualTo(2)
    }

    @Test
    fun subsequentPass_multipliesIntervalByEaseFactor() {
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = 2.5,
            currentInterval = 6,
            currentRepetitions = 2,
            quality = 5
        )
        assertThat(result.interval).isEqualTo(15)
        assertThat(result.repetitions).isEqualTo(3)
    }

    @Test
    fun failQuality_resetsRepetitionsAndInterval() {
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = 2.5,
            currentInterval = 15,
            currentRepetitions = 3,
            quality = 1
        )
        assertThat(result.repetitions).isEqualTo(0)
        assertThat(result.interval).isEqualTo(1)
        assertThat(result.easeFactor).isLessThan(2.5)
    }

    @Test
    fun easeFactor_neverBelow1_3() {
        var ef = 2.5
        repeat(30) {
            val result = SpacedRepetitionEngine.calculate(
                currentEaseFactor = ef,
                currentInterval = 1,
                currentRepetitions = 0,
                quality = 0
            )
            ef = result.easeFactor
        }
        assertThat(ef).isAtLeast(1.3)
    }

    @Test
    fun nextReview_isNowPlusIntervalDays() {
        val before = System.currentTimeMillis()
        val result = SpacedRepetitionEngine.calculate(
            currentEaseFactor = 2.5,
            currentInterval = 0,
            currentRepetitions = 0,
            quality = 5
        )
        val after = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        assertThat(result.nextReview).isAtLeast(before + dayMs)
        assertThat(result.nextReview).isAtMost(after + dayMs + 60_000L)
    }
}
