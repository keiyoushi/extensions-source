package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manhwa18Cc : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

    override val mangaSubString get() = when (lang) {
        "ko" -> "raw"
        else -> "webtoons"
    }

    override fun archiveSelector() = when (lang) {
        "en" -> "div.manga-item:not(:has(h3 a[title$='Raw']))"
        "ko" -> "div.manga-item:has(h3 a[title$='Raw'])"
        else -> "div.manga-item"
    }

    override val archiveUrlSelector = "div.manga-item div.data a"

    // No postId
    override fun Element.postId() = "dummy"
    override fun mangaId(manga: SManga) = ""
    override fun parseArchive(document: Document) = super.parseArchive(document)
        .map {
            it.apply {
                url = memoPath(it)!!
            }
        }

    override suspend fun getPopularManga(page: Int) = archivePage(page, "trending")

    override suspend fun archivePage(page: Int, order: String, path: String, query: String): MangasPage {
        val url = "$baseUrl$path".toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegment(page.toString())
            if (order.isNotBlank()) addQueryParameter("orderby", order)
            if (query.isNotBlank()) addQueryParameter("q", query)
        }.build()
        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("ul.pagination li.next a") != null)
    }

    override val mangaDetailsSelectorDescription = "div.panel-story-description div.dsct"

    override fun chapterListSelector() = "li.a-h"

    override val chapterDateSelector = "span.chapter-time"

    override val pageListParseSelector = "div.read-content img"

    override val filterGenresSelector = ".header-bottom"
    override val genreDirectory = "webtoon-genre"
}
