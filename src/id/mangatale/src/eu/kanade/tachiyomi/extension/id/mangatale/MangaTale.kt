package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document

class MangaTale : MangaThemesia("MangaTale", "https://mangatale.co", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5)
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.headers("Content-Type").contains("application/octet-stream") && response.request.url.toString().endsWith(".jpg")) {
                val newBody = response.body.bytes().toResponseBody("image/jpeg".toMediaTypeOrNull())
                response.newBuilder()
                    .body(newBody)
                    .build()
            } else {
                response
            }
        }
        .build()

    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        thumbnail_url = document.selectFirst(seriesThumbnailSelector)?.imgAttr()
    }
}
