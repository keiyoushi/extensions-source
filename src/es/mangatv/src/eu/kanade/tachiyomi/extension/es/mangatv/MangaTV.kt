package eu.kanade.tachiyomi.extension.es.mangatv

import android.util.Base64
import eu.kanade.tachiyomi.lib.unpacker.Unpacker
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTV : MangaThemesia(
    "Manga  TV",
    "https://mangatv.net",
    "es",
    mangaUrlDirectory = "/lista",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
) {

    override val seriesDescriptionSelector = "b:contains(Sinopsis) + span"

    override fun pageListParse(document: Document): List<Page> {
        val unpackedScript = document.selectFirst("script:containsData(eval)")!!.data()
            .let(Unpacker::unpack)

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(unpackedScript)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson.replace(TRAILING_COMMA_REGEX, "]")).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        return imageList.mapIndexed { i, jsonEl ->
            val encodedLink = jsonEl.jsonPrimitive.content
            val decodedLink = String(Base64.decode(encodedLink, Base64.DEFAULT))
            Page(i, imageUrl = "https:$decodedLink")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(mangaUrlDirectory.substring(1))
            .addQueryParameter("s", query)
            .addQueryParameter("page", page.toString())
        return GET(url.build(), headers)
    }

    // TODO: add demografia, order, tipos, genre
    override fun getFilterList() = FilterList()

    companion object {
        val TRAILING_COMMA_REGEX = """,\s+]""".toRegex()
    }
}
