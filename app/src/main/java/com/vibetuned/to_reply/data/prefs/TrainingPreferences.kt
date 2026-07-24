package com.vibetuned.to_reply.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// One DataStore file per preferences class — two classes must never share a file name or
// DataStore throws "multiple DataStores active for the same file" at runtime.
private val Context.trainingDataStore by preferencesDataStore(name = "to_reply_training_prefs")

/**
 * Rehearsal display settings. Both text-hiding modes leave the speaker name and the line's
 * progress gauge visible: hiding YOUR lines is the memorization drill (can you recall the line
 * with only its timing as a guide?), hiding the OTHERS' trains you to react to the spoken audio
 * instead of reading ahead. Global rather than per-play — it's a practice style, not play data.
 */
class TrainingPreferences(private val context: Context) {

    val hideMyText: Flow<Boolean> = context.trainingDataStore.data.map {
        it[KEY_HIDE_MY_TEXT] ?: false
    }

    val hideOthersText: Flow<Boolean> = context.trainingDataStore.data.map {
        it[KEY_HIDE_OTHERS_TEXT] ?: false
    }

    suspend fun setHideMyText(hide: Boolean) {
        context.trainingDataStore.edit { it[KEY_HIDE_MY_TEXT] = hide }
    }

    suspend fun setHideOthersText(hide: Boolean) {
        context.trainingDataStore.edit { it[KEY_HIDE_OTHERS_TEXT] = hide }
    }

    companion object {
        private val KEY_HIDE_MY_TEXT = booleanPreferencesKey("hide_my_text")
        private val KEY_HIDE_OTHERS_TEXT = booleanPreferencesKey("hide_others_text")
    }
}
