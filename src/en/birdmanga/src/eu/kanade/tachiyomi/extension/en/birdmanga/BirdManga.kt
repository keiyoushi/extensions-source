package eu.kanade.tachiyomi.extension.en.birdmanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException

class BirdManga : MangaThemesia(
    "BirdManga",
    "https://birdmanga.com",
    "en",
) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = super.searchMangaRequest(page, query, filters)
        val url = request.url.newBuilder().apply {
            removeAllQueryParameters("title")
            if (query.isNotBlank()) {
                removePathSegment(0)
                addQueryParameter("s", query)
            }
        }.build()

        return request.newBuilder().url(url).build()
    }

    // Images

    override fun pageListParse(document: Document): List<Page> {
        val imagesData = document.select("script[src*=base64]").firstNotNullOfOrNull {
            val data = String(Base64.decode(it.attr("src").substringAfter("base64,"), Base64.DEFAULT))
            JSON_IMAGE_LIST_REGEX.find(data)?.destructured?.toList()?.get(0)
        } ?: return super.pageListParse(document)

        val imageList = try {
            json.parseToJsonElement(imagesData).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

        val chapterUrl = document.location()
        return imageList.mapIndexed { i, jsonEl ->
            Page(i, chapterUrl, jsonEl.jsonPrimitive.content)
        }
    }
}
