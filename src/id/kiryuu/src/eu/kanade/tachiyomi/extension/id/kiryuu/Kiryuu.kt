package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Kiryuu : MangaThemesia("Kiryuu", "https://kiryuu02.com", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime != "application/octet-stream") {
                    return@addInterceptor response
                }
                // Fix image content type
                val type = IMG_CONTENT_TYPE.toMediaType()
                val body = response.body.source().asResponseBody(type)
                return@addInterceptor response.newBuilder().body(body).build()
            }
            response
        }
        .rateLimit(4)
        .build()

    override fun Element.imgAttr(): String = when {
        hasAttr("data-lzl-src") -> attr("abs:data-lzl-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    // manga details
    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        title = document.selectFirst(seriesThumbnailSelector)!!.attr("title")
    }

    override val hasProjectPage = true
}

private const val IMG_CONTENT_TYPE = "image/jpeg"
