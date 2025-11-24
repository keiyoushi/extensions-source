package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale

class MangaStop : MangaThemesia(
    "Manga Stop",
    "https://mangastop.net",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")),
) {
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "application/xhtml+xml")

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()

        MangaThemesia.JSON_IMAGE_LIST_REGEX.find(html)?.groupValues?.get(1)?.let {
            runCatching {
                val pages = json.parseToJsonElement(it).jsonArray.mapIndexed { i, jsonEl ->
                    Page(i, response.request.url.toString(), jsonEl.jsonPrimitive.content)
                }
                if (pages.isNotEmpty()) return pages
            }
        }

        val document = Jsoup.parse(html, response.request.url.toString())
        return super.pageListParse(document).filterNot {
            it.imageUrl?.contains("mihon", true) == true
        }
    }
}
