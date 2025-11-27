package eu.kanade.tachiyomi.extension.pt.mangastop

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.nodes.Document
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
        .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .set("Accept-Language", "pt-BR,pt;q=0.8,en-US;q=0.5,en;q=0.3")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "none")
        .set("Sec-Fetch-User", "?1")
        .set("Upgrade-Insecure-Requests", "1")

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        val pages = super.pageListParse(document)
            .filterNot { it.imageUrl?.contains("mihon", true) == true }

        if (pages.isNotEmpty()) return pages

        return MangaThemesia.JSON_IMAGE_LIST_REGEX.find(document.toString())
            ?.groupValues?.get(1)
            ?.let { json.parseToJsonElement(it).jsonArray }
            ?.mapIndexed { i, el ->
                Page(i, document.location(), el.jsonPrimitive.content)
            }
            .orEmpty()
    }
}
