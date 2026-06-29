package eu.kanade.tachiyomi.extension.pt.mangasbrasuka

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.rateLimit
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangasBrasuka :
    Madara(
        "Mangas Brasuka",
        "https://mangasbrasuka.com.br",
        "pt-BR",
        SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val useNewChapterEndpoint = true

    override fun pageListParse(document: Document): List<Page> {
        val redirectUrl = document.selectFirst("div.page-break a")!!.absUrl("href")
        val pageUrl = redirectUrl.toHttpUrl().queryParameter("a")!!.toHttpUrl().toUrl()

        val url = "$baseUrl/campanha.php".toHttpUrl().newBuilder()
            .addQueryParameter("auth", pageUrl.toString())
            .build()

        return client.newCall(GET(url, headers)).execute().asJsoup()
            .select(".manga-content img").mapIndexed { index, element ->
                Page(index, imageUrl = element.absUrl("src"))
            }
    }
}
