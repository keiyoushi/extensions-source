package eu.kanade.tachiyomi.extension.ar.iimanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale

class ARESManga : MangaThemesia(
    "ARESManga",
    "https://fl-ares.com",
    "ar",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar")),
) {
    // Source renamed a while back
    override val id = 230017529540228175

    override val seriesAuthorSelector = ".imptdt:contains(المؤلف) i"
    override val seriesArtistSelector = ".imptdt:contains(الرسام) i"
    override val seriesTypeSelector = ".imptdt:contains(النوع) i"
    override val seriesStatusSelector = ".imptdt:contains(الحالة) i"

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
            Page(i, imageUrl = jsonEl.jsonPrimitive.content)
        }
    }
}
