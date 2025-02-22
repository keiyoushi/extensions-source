package eu.kanade.tachiyomi.extension.id.comicaso

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Comicaso : MangaThemesia(
    "Comicaso",
    "https://comicaso.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("id")),
) {
    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script[src^=data:text/javascript;base64,]").map {
            Base64.decode(
                it.attr("src").substringAfter("base64,"),
                Base64.DEFAULT,
            ).toString(Charsets.UTF_8)
        }.firstOrNull { it.startsWith("ts_reader.run") }
            ?: throw Exception("Couldn't find page script")

        countViews(document)

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(script)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            Page(i, document.location(), jsonEl.jsonPrimitive.content)
        }

        return scriptPages
    }
}
