package eu.kanade.tachiyomi.extension.id.mangatale

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document

class MangaTale : MangaThemesia("MangaTale", "https://mangatale.co", "id") {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(20, 5)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime != "application/octet-stream") {
                    return@addInterceptor response
                }
                // Fix image content type
                val type = IMG_CONTENT_TYPE.toMediaType()
                val body = response.body.bytes().toResponseBody(type)
                return@addInterceptor response.newBuilder().body(body)
                    .header("Content-Type", IMG_CONTENT_TYPE).build()
            }
            response
        }
        .build()

    override val seriesTitleSelector = ".ts-breadcrumb li:last-child span"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        thumbnail_url = document.selectFirst(seriesThumbnailSelector)?.imgAttr()
    }

    companion object {
        private const val IMG_CONTENT_TYPE = "image/jpeg"
    }
}
