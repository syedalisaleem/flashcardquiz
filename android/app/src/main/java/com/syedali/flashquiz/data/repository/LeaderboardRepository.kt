package com.syedali.flashquiz.data.repository

import com.syedali.flashquiz.BuildConfig
import com.syedali.flashquiz.auth.AccountManager
import com.syedali.flashquiz.model.LeaderboardBoard
import com.syedali.flashquiz.model.LeaderboardEntry
import com.syedali.flashquiz.network.BackendLeaderboardProgress
import com.syedali.flashquiz.network.BackendService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online leaderboard: pushes this device's learnt totals (signed-in only)
 * and fetches the globally ranked board from the backend.
 */
@Singleton
class LeaderboardRepository @Inject constructor(
    private val backend: BackendService,
    private val deckRepo: DeckRepository
) {

    val isConfigured: Boolean get() = BuildConfig.BACKEND_URL.isNotBlank()

    val isSignedIn: Boolean get() = AccountManager.isLoggedIn()

    /** Push current totals (best effort), then fetch the board sorted by [sort]. */
    suspend fun load(sort: String): Result<LeaderboardBoard> {
        reportProgress()
        return runCatching {
            val response = backend.leaderboard(sort)
            LeaderboardBoard(
                sort = sort,
                total = response.total ?: 0,
                entries = response.entries.orEmpty().map { e ->
                    LeaderboardEntry(
                        rank = e.rank ?: 0,
                        uid = e.uid.orEmpty(),
                        name = e.name.orEmpty(),
                        cards = e.cards ?: 0,
                        mcqs = e.mcqs ?: 0
                    )
                }
            )
        }
    }

    /** Best-effort upload of the signed-in user's learnt totals. */
    private suspend fun reportProgress() {
        if (!isSignedIn) return
        val uid = AccountManager.getUserId()
        if (uid.isBlank()) return
        val name = AccountManager.getDisplayName()
            .ifBlank { AccountManager.getEmail().substringBefore('@') }
            .ifBlank { "Learner" }
        val totals = deckRepo.getLearntTotals()
        try {
            backend.reportProgress(
                BackendLeaderboardProgress(uid = uid, name = name, cards = totals.cards, mcqs = totals.mcqs)
            )
        } catch (_: Exception) {
            // The board still loads without a fresh push; retried on next open.
        }
    }
}
