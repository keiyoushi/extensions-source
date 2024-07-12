package eu.kanade.tachiyomi.extension.en.nyraxmanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException

class NyraxManga : MangaThemesia(
    "Nyrax Manga",
    "https://nyraxmanga.com",
    "en",
) {
    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            // Add 'color' badge as a genre
            if (document.selectFirst(".thumb .colored") != null) {
                genre = genre?.plus(", Color")
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        // "ts_reader.run({" in base64
        val script = document.selectFirst("script[src^=data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7]")
            ?: return super.pageListParse(document)
        val data = Base64.decode(script.attr("src").substringAfter("base64,"), Base64.DEFAULT).toString(Charsets.UTF_8)
        val imageListJson = JSON_IMAGE_LIST_REGEX.find(data)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }

        return imageList.mapIndexed { i, jsonEl ->
            Page(i, document.location(), jsonEl.jsonPrimitive.content)
        }
    }
}
