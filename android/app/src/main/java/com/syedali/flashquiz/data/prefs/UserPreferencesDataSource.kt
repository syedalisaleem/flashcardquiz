package com.syedali.flashquiz.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesDataSource(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val THEME_ID = stringPreferencesKey("theme_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val DAILY_GOAL = longPreferencesKey("daily_goal")
    }

    val themeId: Flow<String> = dataStore.data.map { it[THEME_ID] ?: "dark" }
    val displayName: Flow<String> = dataStore.data.map { it[DISPLAY_NAME] ?: "Learner" }
    val dailyGoal: Flow<Long> = dataStore.data.map { it[DAILY_GOAL] ?: 20L }

    suspend fun setThemeId(id: String) {
        dataStore.edit { it[THEME_ID] = id }
    }

    suspend fun setDisplayName(name: String) {
        dataStore.edit { it[DISPLAY_NAME] = name }
    }

    suspend fun setDailyGoal(goal: Long) {
        dataStore.edit { it[DAILY_GOAL] = goal }
    }
}
