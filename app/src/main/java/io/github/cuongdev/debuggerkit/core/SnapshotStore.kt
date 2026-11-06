package io.github.cuongdev.debuggerkit.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Remembers the original value of every setting before the dev panel first changes it,
 * so "Restore original" can put the phone back exactly as it was.
 */
class SnapshotStore(context: Context) {
    private val sp = context.getSharedPreferences("snapshot", Context.MODE_PRIVATE)
    private val _savedAt = MutableStateFlow(sp.getLong(KEY_AT, 0L))
    val savedAt: StateFlow<Long> = _savedAt.asStateFlow()

    fun has(id: String): Boolean = sp.contains(PREFIX + id)

    @Synchronized
    fun save(id: String, value: String?) {
        if (has(id)) return
        val e = sp.edit().putString(PREFIX + id, value ?: NULL)
        if (!sp.contains(KEY_AT)) e.putLong(KEY_AT, System.currentTimeMillis())
        e.commit()
        _savedAt.value = sp.getLong(KEY_AT, 0L)
    }

    fun all(): Map<String, String?> = sp.all
        .filterKeys { it.startsWith(PREFIX) }
        .map { (k, v) -> k.removePrefix(PREFIX) to (v as? String)?.takeIf { it != NULL } }
        .toMap()

    @Synchronized
    fun remove(id: String) {
        sp.edit().remove(PREFIX + id).commit()
        if (all().isEmpty()) clear()
    }

    @Synchronized
    fun clear() {
        sp.edit().clear().commit()
        _savedAt.value = 0L
    }

    companion object {
        private const val PREFIX = "v_"
        private const val KEY_AT = "saved_at"
        private const val NULL = "\u0000null"
    }
}
