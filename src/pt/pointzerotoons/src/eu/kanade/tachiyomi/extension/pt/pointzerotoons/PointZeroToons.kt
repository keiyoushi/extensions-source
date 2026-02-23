package eu.kanade.tachiyomi.extension.pt.pointzerotoons

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

class PointZeroToons :
    MangaThemesia(
        "Point Zero Toons",
        "https://kitsuneyako.com",
        "pt-BR",
        dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val seriesThumbnailSelector = ".tx-hero-cover > img.wp-post-image"

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
