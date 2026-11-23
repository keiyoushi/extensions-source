package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import java.io.IOException

/** Answers the store's page addresses with the page's image. */
internal class PageInterceptor(private val store: VolumeStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val page = try {
            store.page(request.url, chain.call()::isCanceled)
        } catch (e: IOException) {
            throw e
        } catch (e: Throwable) {
            // Anything but an IOException thrown from an interceptor crashes the app
            throw IOException(e.message, e)
        } ?: return chain.proceed(request)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(page.image.asResponseBody(VolumeFormat.AVIF, page.length))
            .build()
    }
}
