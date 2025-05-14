package eu.kanade.tachiyomi.extension.ja.comictop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ComicTop : ParsedHttpSource() {

    override val name = "ComicTop"

    override val baseUrl = "https://comic-top.com/"

    override val lang = "ja"

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".ホットマンガ a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")?.substringBefore("?")
        title = element.attr("title")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    
}
