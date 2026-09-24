package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
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

    override fun nextPageSelector() = "ul.pagination li.next a"

    override val orderQueryParameter = "orderby"
    override val searchQueryParameter = "q"
    override fun archiveUrlBuilder(
        page: Int,
        order: String,
        path: String,
        query: String,
    ) = super.archiveUrlBuilder(page, order, path, query).apply {
        if (page > 1) removePathSegment(0)
    }
    override fun searchUrlBuilder(query: String) = super.searchUrlBuilder(query).addPathSegment("search")

    override val mangaDetailsSelectorDescription = "div.panel-story-description div.dsct"

    override fun chapterListSelector() = "li.a-h"

    override val chapterDateSelector = "span.chapter-time"

    override val pageListParseSelector = "div.read-content img"

    override val filterGenresSelector = ".header-bottom"
    override val genreDirectory = "webtoon-genre"
}
