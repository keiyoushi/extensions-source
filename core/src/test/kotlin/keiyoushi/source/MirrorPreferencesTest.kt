package keiyoushi.source

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MirrorPreferencesTest {

    private val defaults = arrayOf(
        "Site A" to "https://a1.example",
        "Site B" to "https://b1.example",
        "Site C" to "https://c1.example",
    )

    private fun preferences(pick: Int? = null) = MemoryPreferences().apply {
        if (pick != null) edit().putString("preferred_mirror", pick.toString()).apply()
    }

    private fun mirrorPreferences(preferences: SharedPreferences, defaults: Array<Pair<String, String>> = this.defaults) = MirrorPreferences(preferences, defaults, "Mirror")

    @Test
    fun pickFollowsItsUrlToAnotherPlace() {
        val mirrors = mirrorPreferences(preferences(pick = 1))
        mirrors.update(listOf("https://a1.example", "https://c1.example", "https://b1.example"))
        assertEquals("https://b1.example", mirrors.baseUrl)
    }

    @Test
    fun pickKeepsItsPlaceWhenItsDomainMoves() {
        val mirrors = mirrorPreferences(preferences(pick = 1))
        mirrors.update(listOf("https://a1.example", "https://b2.example", "https://c1.example"))
        assertEquals("https://b2.example", mirrors.baseUrl)
    }

    @Test
    fun pickFollowsItsUrlInAListOfAnotherSize() {
        val mirrors = mirrorPreferences(preferences(pick = 2))
        mirrors.update(listOf("https://a1.example", "https://d1.example", "https://e1.example", "https://c1.example"))
        assertEquals("https://c1.example", mirrors.baseUrl)
    }

    @Test
    fun pickGoesBackToTheFirstMirrorWhenGoneFromAListOfAnotherSize() {
        val mirrors = mirrorPreferences(preferences(pick = 2))
        mirrors.update(listOf("https://a1.example", "https://b1.example", "https://c2.example", "https://d1.example"))
        assertEquals("https://a1.example", mirrors.baseUrl)
    }

    @Test
    fun defaultFollowsTheSiteOrder() {
        val mirrors = mirrorPreferences(preferences())
        mirrors.update(listOf("https://b1.example", "https://a1.example", "https://c1.example"))
        assertEquals("https://b1.example", mirrors.baseUrl)
    }

    @Test
    fun newDslUrlsDropWhatWasFetched() {
        val preferences = preferences(pick = 1)
        mirrorPreferences(preferences).update(listOf("https://a2.example", "https://b2.example", "https://c2.example"))
        val newDefaults = arrayOf("Site A" to "https://a3.example", "Site B" to "https://b3.example")
        val mirrors = mirrorPreferences(preferences, newDefaults)
        assertEquals(newDefaults.asList(), mirrors.mirrors)
        assertEquals("https://a3.example", mirrors.baseUrl)
        assertNull(preferences.getString("fetchedMirrors", null))
        assertNull(preferences.getString("preferred_mirror", null))
    }
}

/** [SharedPreferences] kept in memory. */
private class MemoryPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = values[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearing = false

        private fun put(key: String, value: Any?): SharedPreferences.Editor {
            changes[key] = value
            return this
        }

        override fun putString(key: String, value: String?) = put(key, value)

        override fun putStringSet(key: String, values: Set<String>?) = put(key, values)

        override fun putInt(key: String, value: Int) = put(key, value)

        override fun putLong(key: String, value: Long) = put(key, value)

        override fun putFloat(key: String, value: Float) = put(key, value)

        override fun putBoolean(key: String, value: Boolean) = put(key, value)

        override fun remove(key: String): SharedPreferences.Editor {
            removals += key
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearing = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearing) values.clear()
            removals.forEach(values::remove)
            values.putAll(changes)
        }
    }
}
