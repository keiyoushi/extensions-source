package eu.kanade.tachiyomi.extension.ar.iimanga

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale

class ARESMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        EnARESManga(),
        ARESManga("ARESNOV", "https://aresnov.org"),
    )
}

class EnARESManga : ARESManga("ARESManga", "https://en-aresmanga.com") {
    // The scanlator changed their name.
    override val id = 230017529540228175
}

open class ARESManga(
    name: String,
    baseUrl: String,
) : MangaThemesia(
    name,
    baseUrl,
    "ar",
    mangaUrlDirectory = "/series",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("ar")),
) {
    override val seriesAuthorSelector = ".imptdt:contains(بواسطة) i"
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
