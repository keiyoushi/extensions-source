package eu.kanade.tachiyomi.extension.fr.aniverse

import android.content.SharedPreferences

/**
 * Works out which chapter the user is reading from the page lists Mihon's reader asks for.
 *
 * The reader only exposes the chapters it loads: the one opened, then the next one ahead of time
 * (as soon as the opened one is loaded on unmetered networks, near its last pages otherwise), and
 * the previous one when scrolling back past the first page. Loading the chapter that follows the
 * last one loaded therefore means the user is now in that last one.
 */
class ReadTracker(private val preferences: SharedPreferences) {

    /**
     * Records that the reader loaded the chapter at [index] (in the site's ascending chapter list)
     * of manga [slug], and returns the index of the chapter the user is reading if it wasn't
     * reported already, or null.
     */
    @Synchronized
    fun onReaderLoad(slug: String, index: Int): Int? {
        val previous = preferences.getInt(LAST_LOADED_PREFIX + slug, -1)
        val step = index - previous
        val reading = when {
            previous < 0 -> index
            step in 1..MAX_STEP -> previous
            // Scrolling back to the previous chapter, the user hasn't left the current one.
            step in -MAX_STEP..0 -> return null
            else -> index
        }
        val reported = preferences.getInt(LAST_REPORTED_PREFIX + slug, -1)
        preferences.edit()
            .putInt(LAST_LOADED_PREFIX + slug, index)
            .putInt(LAST_REPORTED_PREFIX + slug, reading)
            .apply()
        return reading.takeIf { it != reported }
    }

    private companion object {
        const val LAST_LOADED_PREFIX = "reader_loaded_"
        const val LAST_REPORTED_PREFIX = "reader_reported_"

        // Allows for chapters the reader skips (duplicates, filtered scanlators).
        const val MAX_STEP = 3
    }
}
