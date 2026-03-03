package eu.kanade.tachiyomi.extension.pt.pinkrosa

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document

class PinkRosa :
    ZeistManga(
        "Pink Rosa",
        "https://scanpinkrosa.blogspot.com",
        "pt-BR",
    ) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int) = super.fetchLatestUpdates(page)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("#syn_bod")?.text()
        thumbnail_url = document.selectFirst(".thum")?.attr("style")?.let {
            THUMBNAIL_REGEX.find(it)?.groups?.get(1)?.value
        }
        author = document.selectFirst("#tauther")?.text()
        genre = document.select("a[href*=label][rel]").joinToString { it.text() }
        setUrlWithoutDomain(document.location())
    }

    override fun getChapterFeedUrl(doc: Document): String {
        val label = doc.selectFirst(".chapter_get")!!.attr("data-labelchapter")
        return "$baseUrl/feeds/posts/default/-".toHttpUrl().newBuilder()
            .addPathSegment(label)
            .addQueryParameter("alt", "json")
            .addQueryParameter("start-index", "1")
            .addQueryParameter("max-results", "999")
            .build().toString()
    }

    override val pageListSelector = "div.separator a"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(pageListSelector).mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("href"))
        }
    }

    companion object {
        val THUMBNAIL_REGEX = """url."([^"]+)""".toRegex()
    }
}
