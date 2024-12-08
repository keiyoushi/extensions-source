package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.multisrc.etoshore.Etoshore
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.Response
import org.jsoup.nodes.Element

class MangaLivre : Etoshore(
    name = "Manga Livre",
    baseUrl = "https://mangalivre.one",
    lang = "pt-BR",
) {
    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun chapterListParse(response: Response) =
        super.chapterListParse(response).reversed()

    override fun imageFromElement(element: Element): String? = element.attr("abs:src")
}
