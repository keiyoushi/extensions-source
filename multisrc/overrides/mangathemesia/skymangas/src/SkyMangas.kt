package eu.kanade.tachiyomi.extension.es.skymangas

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale

class SkyMangas : MangaThemesia(
    "SkyMangas",
    "https://skymangas.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("div.readercontent > div.wrapper > script")
            ?: return super.pageListParse(document)

        val scriptSrc = script.attr("src")

        if (scriptSrc.startsWith("data:text/javascript;base64,")) {
            val encodedData = scriptSrc.substringAfter("data:text/javascript;base64,")
            val decodedData = Base64.decode(encodedData, Base64.DEFAULT).toString(Charsets.UTF_8)

            val imageListJson = JSON_IMAGE_LIST_REGEX.find(decodedData)?.destructured?.toList()?.get(0).orEmpty()
            val imageList = try {
                json.parseToJsonElement(imageListJson).jsonArray
            } catch (_: IllegalArgumentException) {
                emptyList()
            }

            return imageList.mapIndexed { i, jsonEl ->
                Page(i, document.location(), jsonEl.jsonPrimitive.content)
            }
        }

        return super.pageListParse(document)
    }
}
