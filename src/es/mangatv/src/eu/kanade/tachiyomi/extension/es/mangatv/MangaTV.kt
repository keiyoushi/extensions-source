package eu.kanade.tachiyomi.extension.es.mangatv

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTV : MangaThemesia(
    "Manga  TV",
    "https://www.mangatv.net",
    "es",
    mangaUrlDirectory = "/lista",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
) {

    override val seriesDescriptionSelector = "b:contains(Sinopsis) + span"

    override fun pageListParse(document: Document): List<Page> {
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(document.toString())?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson.replace(TRAILING_COMMA_REGEX, "]")).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        return imageList.mapIndexed { i, jsonEl ->
            Page(i, imageUrl = "https:${jsonEl.jsonPrimitive.content}")
        }
    }

    companion object {
        val TRAILING_COMMA_REGEX = """,\s+]""".toRegex()
    }
}
