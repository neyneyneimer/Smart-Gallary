package ai.sakana.aura.gallery.diagnostics

import android.content.Context

/** Persists a minimal crash marker so the next launch can show a safe recovery message. */
internal class CrashRecoveryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun markFatalCrash() {
        preferences.edit().putBoolean(KEY_RECOVER_FROM_CRASH, true).commit()
    }

    fun consumeFatalCrash(): Boolean {
        val wasMarked = preferences.getBoolean(KEY_RECOVER_FROM_CRASH, false)
        if (wasMarked) preferences.edit().remove(KEY_RECOVER_FROM_CRASH).commit()
        return wasMarked
    }

    private companion object {
        const val PREFERENCES = "aura_recovery"
        const val KEY_RECOVER_FROM_CRASH = "recover_from_fatal_crash"
    }
}
