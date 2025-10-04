package eu.kanade.tachiyomi.extension.en.bookwalker

import android.util.Log
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.LinkedHashMap
import kotlin.time.Duration.Companion.minutes

class BookWalkerImageRequestInterceptor(private val prefs: BookWalkerPreferences) : Interceptor {

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

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.headers[HEADER_IS_REQUEST_FROM_EXTENSION] != "true") {
            return chain.proceed(request)
        }

        val readerUrl = request.url.toString()
        val pageIndex = request.headers[HEADER_PAGE_INDEX]!!.toInt()

        Log.d("bookwalker", "Intercepting request for page $pageIndex")

        val reader = synchronized(readerQueue) {
            readerQueue.getOrPut(readerUrl) {
                Expiring(BookWalkerChapterReader(readerUrl, prefs), READER_EXPIRATION_TIME) {
                    disposeReader(this)
                }
            }
        }

        val imageData = try {
            runBlocking {
                reader.contents.getPage(pageIndex)
            }
        } catch (e: BookWalkerChapterReader.NonFatalReaderException) {
            // Just re-throw, this isn't worth killing the webview over.
            Log.e("bookwalker", e.toString())
            throw e
        } catch (e: Exception) {
            // If there's some other exception, we can generally assume that the
            // webview is broken in some way and should be re-created.
            reader.expire()
            Log.e("bookwalker", e.toString())
            throw e
        }

        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(imageData.toResponseBody(IMAGE_MEDIA_TYPE))
            .build()
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

        private val IMAGE_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
