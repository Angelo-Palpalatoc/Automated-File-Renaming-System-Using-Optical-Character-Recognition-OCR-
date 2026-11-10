package com.local.secrename

import android.content.SharedPreferences
import android.net.Uri

internal data class BatchProgress(
    val matches: Boolean,
    val saved: Set<String>,
    val skipped: Set<String>,
) {
    val completedIds: Set<String> get() = saved + skipped
}

/** Stores batch completion records for one source and output folder pair. */
internal class BatchProgressStore(private val preferences: SharedPreferences) {
    fun read(source: Uri, output: Uri): BatchProgress {
        val matches = preferences.getString(IDENTITY, null) == identity(source, output)
        return BatchProgress(
            matches = matches,
            saved = if (matches) preferences.getStringSet(SAVED, emptySet()).orEmpty().toSet() else emptySet(),
            skipped = if (matches) preferences.getStringSet(SKIPPED, emptySet()).orEmpty().toSet() else emptySet(),
        )
    }

    fun reconcile(saved: Set<String>, skipped: Set<String>) {
        check(preferences.edit().putStringSet(SAVED, saved).putStringSet(SKIPPED, skipped).commit()) {
            "Batch progress could not be updated."
        }
    }

    fun markAlreadyProcessed(source: Uri, output: Uri, id: String) {
        val saved = preferences.getStringSet(SAVED, emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        val skipped = preferences.getStringSet(SKIPPED, emptySet()).orEmpty().toMutableSet().apply { add(id) }
        check(preferences.edit().putString(IDENTITY, identity(source, output))
            .putStringSet(SAVED, saved).putStringSet(SKIPPED, skipped).commit()) {
            "Batch progress could not be saved."
        }
    }

    fun markSaved(source: Uri, output: Uri, id: String, clearSkipped: Boolean) {
        val saved = preferences.getStringSet(SAVED, emptySet()).orEmpty().toMutableSet().apply { add(id) }
        check(preferences.edit().putString(IDENTITY, identity(source, output)).putStringSet(SAVED, saved).commit()) {
            "Batch progress could not be saved."
        }
        if (clearSkipped) {
            val skipped = preferences.getStringSet(SKIPPED, emptySet()).orEmpty().toMutableSet().apply { remove(id) }
            check(preferences.edit().putStringSet(SKIPPED, skipped).commit()) {
                "Batch progress could not be saved."
            }
        }
    }

    private fun identity(source: Uri, output: Uri): String = source.toString() + "|" + output.toString()

    private companion object {
        const val IDENTITY = "batch.identity"
        const val SAVED = "batch.saved"
        const val SKIPPED = "batch.skipped"
    }
}
