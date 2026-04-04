package eu.kanade.tachiyomi.extension.en.bookwalker

import kotlinx.coroutines.runBlocking
import java.util.LinkedHashMap
import kotlin.time.Duration.Companion.minutes

class BookWalkerChapterReaderManager(private val prefs: BookWalkerPreferences) {

    private val readerQueue = object : LinkedHashMap<String, Expiring<BookWalkerChapterReader>>(
        MAX_ACTIVE_READERS + 1,
        1f,
        true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Expiring<BookWalkerChapterReader>>): Boolean {
            if (size > MAX_ACTIVE_READERS) {
                eldest.value.expire()
                return true
            }
            return false
        }
    }

    fun getReader(readerUrl: String): Expiring<BookWalkerChapterReader> = synchronized(readerQueue) {
        readerQueue.getOrPut(readerUrl) {
            Expiring(BookWalkerChapterReader(readerUrl, prefs), READER_EXPIRATION_TIME) {
                disposeReader(this)
            }
        }
    }

    private fun disposeReader(reader: BookWalkerChapterReader) {
//        Log.d("bookwalker", "Disposing reader ${reader.readerUrl}")
        synchronized(readerQueue) {
            readerQueue.remove(reader.readerUrl)
        }
        runBlocking {
            reader.destroy()
        }
    }

    companion object {
        // Having at most two chapter readers at once should be enough to avoid thrashing on chapter
        // transitions without keeping an excessive number of webviews running in the background.
        // In theory there could also be a download happening at the same time, but 2 is probably fine.
        private const val MAX_ACTIVE_READERS = 2

        // We don't get events when a user exits the reader or a download finishes, so we want to
        // make sure to clean up unused readers after a period of time.
        private val READER_EXPIRATION_TIME = 1.minutes
    }
}
