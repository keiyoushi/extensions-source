package eu.kanade.tachiyomi.extension.id.komikhwa

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class Komikhwa :
    MangaThemesia(
        "Komikhwa",
        "https://komikhwa.com",
        "id",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
    ) {
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
