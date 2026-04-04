package eu.kanade.tachiyomi.extension.en.bookwalker

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class BookWalkerImageRequestInterceptor(private val readerManager: BookWalkerChapterReaderManager) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.headers[HEADER_IS_REQUEST_FROM_EXTENSION] != "true") {
            return chain.proceed(request)
        }

        val readerUrl = request.url.toString()
        val pageIndex = request.headers[HEADER_PAGE_INDEX]!!.toInt()

        Log.d("bookwalker", "Intercepting request for page $pageIndex")

        val reader = readerManager.getReader(readerUrl)

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

        return when (imageData) {
            is BookWalkerChapterReader.ImageData.Bytes -> Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(imageData.bytes.toResponseBody(IMAGE_MEDIA_TYPE))
                .build()
            is BookWalkerChapterReader.ImageData.URL -> chain.proceed(GET(imageData.url))
        }
    }

    companion object {
        private val IMAGE_MEDIA_TYPE = "image/jpeg".toMediaType()
    }
}
