package eu.kanade.tachiyomi.extension.es.mangatv

import android.util.Base64
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTV : MangaThemesia(
    "Manga TV",
    "https://www.mangatv.net",
    "es",
    mangaUrlDirectory = "/lista",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
) {

    override val id = 7214040353404261084
    // {"name": "Manga  TV", "lang": "es"}

    override val seriesDescriptionSelector = "b:contains(Sinopsis) + span"

    override fun pageListParse(document: Document): List<Page> {
        // Bypass packer obfuscation
        val script = document.selectFirst(".readingnav")?.nextElementSibling()!!.html().toString()
        val decoded = QuickJs.create().use { quickJs -> quickJs.evaluate(script.removePrefix("eval")) }.toString()

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(decoded)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson.replace(TRAILING_COMMA_REGEX, "]")).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

        fun ByteArray.asUrlString(): String {
            return "https:" + String(this, Charsets.UTF_8)
        }

        return imageList.mapIndexed { i, jsonEl ->
            Page(i, imageUrl = Base64.decode(jsonEl.jsonPrimitive.content, 0).asUrlString())
        }
    }

    companion object {
        val TRAILING_COMMA_REGEX = """,\s+]""".toRegex()
    }
}
