package eu.kanade.tachiyomi.extension.pt.mugiwarasoficial

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MugiwarasOficial :
    Madara(
        "Mugiwaras Oficial",
        "https://mugiwarasoficial.com",
        "pt-BR",
        SimpleDateFormat("d 'de' MMM 'de' yyyy", Locale("pt", "BR")),
    ) {

    override val client = super.client.newBuilder()
        .rateLimit(3, 1)
        .build()

    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override fun pageListParse(document: Document): List<Page> {
        val redirectUrl = document.selectFirst("div.page-break a")!!.absUrl("href")
        val pageUrl = redirectUrl.toHttpUrl().queryParameter("t")!!.toHttpUrl().toUrl()

        val url = "$baseUrl/campanha.php".toHttpUrl().newBuilder()
            .addQueryParameter("auth", pageUrl.toString())
            .build()

        return client.newCall(GET(url, headers)).execute().asJsoup()
            .select(".manga-content img").mapIndexed { index, elemet ->
                Page(index, imageUrl = elemet.absUrl("src"))
            }
    }
}
