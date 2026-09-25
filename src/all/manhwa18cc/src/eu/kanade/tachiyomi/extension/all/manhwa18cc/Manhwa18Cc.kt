package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.get
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Manhwa18Cc : MadaraNoAjax() {
    override val supportsPostId = false
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
        if (page > 1) removePathSegment(1)
    }
    override fun searchUrlBuilder(page: Int, query: String) = super.searchUrlBuilder(page, query).apply {
        if (page > 1) {
            removePathSegment(1)
            addQueryParameter("page", page.toString())
        }
        setPathSegment(0, "search")
    }

    override val mangaDetailsSelectorDescription = "div.panel-story-description div.dsct"

    override fun chapterListSelector() = "li.a-h"

    override val chapterDateSelector = "span.chapter-time"

    override val pageListParseSelector = "div.read-content img"

    override val filterGenresSelector = ".header-bottom"
    override val genreDirectory = "webtoon-genre"
}
