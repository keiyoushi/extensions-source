package eu.kanade.tachiyomi.extension.en.erosscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

@Source
abstract class ErosScans : MangaThemesia() {

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        val altNames = document.selectFirst(seriesAltNameSelector)?.ownText()
            ?.split(" • ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }

        if (!altNames.isNullOrEmpty()) {
            val baseDescription = description.orEmpty()
                .substringBefore(altNamePrefix)
                .trim()

            description = buildString {
                append(baseDescription)
                if (isNotEmpty()) append("\n\n")
                append(altNamePrefix.trim())
                append("\n")
                altNames.joinTo(this, "\n") { "- $it" }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script[src^=data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7]")
            ?: return super.pageListParse(document)

        val decoded = Base64.decode(script.attr("src").substringAfter("base64,"), Base64.DEFAULT).decodeToString()

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(decoded)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = try {
            json.parseToJsonElement(imageListJson).jsonArray
        } catch (_: IllegalArgumentException) {
            return super.pageListParse(document)
        }

        return imageList.mapIndexed { index, element ->
            Page(index, imageUrl = element.jsonPrimitive.content)
        }
    }
}
