package eu.kanade.tachiyomi.extension.es.nekoscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class NekoScans : MangaThemesia(
    "NekoScans",
    "https://nekoscans.com",
    "es",
    mangaUrlDirectory = "/proyecto",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val client = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val seriesStatusSelector = ".tsinfo .imptdt:contains(estado) i"

    override fun pageListParse(document: Document): List<Page> {
        val chapterUrl = document.location()
        val htmlPages = document.select(pageSelector)
            .filterNot { it.imgAttr().isEmpty() }
            .mapIndexed { i, img -> Page(i, chapterUrl, img.imgAttr()) }

        countViews(document)

        // Some sites also loads pages via javascript
        if (htmlPages.isNotEmpty()) { return htmlPages }

        var docString = document.toString()

        document.select("div#content > div.wrapper > script[src^=data:text/javascript;base64,]").forEach { script ->
            val scriptText = String(Base64.decode(script.attr("src").substringAfter("base64,"), Base64.DEFAULT))
            docString += scriptText
        }

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(docString)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
        val scriptPages = imageList.mapIndexed { i, jsonEl ->
            Page(i, chapterUrl, jsonEl.jsonPrimitive.content)
        }

        return scriptPages
    }
}
