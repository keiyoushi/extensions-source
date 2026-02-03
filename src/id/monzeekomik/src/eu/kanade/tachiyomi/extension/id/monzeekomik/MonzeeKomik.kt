package eu.kanade.tachiyomi.extension.id.monzeekomik

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document

class MonzeeKomik :
    MangaThemesia(
        "Monzee Komik",
        "https://monzee01.my.id",
        "id",
    ) {
    override val hasProjectPage = true

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script[src^='data:text/javascript;base64,dHNfcmVhZGVyLnJ1bih7']")
            ?: return super.pageListParse(document)

        val data = script.attr("src").substringAfter("base64,").let { encoded ->
            Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
        }

        val images = JSON_IMAGE_LIST_REGEX.find(data)?.destructured?.toList()?.get(0).orEmpty().let { jsonString ->
            try {
                json.parseToJsonElement(jsonString).jsonArray
            } catch (_: Exception) {
                emptyList()
            }
        }

        return images.mapIndexed { index, element ->
            Page(index, imageUrl = element.jsonPrimitive.content)
        }
    }
}
