package eu.kanade.tachiyomi.extension.en.mangablaze

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MangaBlaze :
    Madara(
        "MangaBlaze",
        "https://mangablaze.com",
        "en",
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override fun popularMangaSelector() = "article.x72-col"
    override val popularMangaUrlSelector = "h3.x72-title a"

    override fun searchMangaSelector() = "article.z8x-card"
    override val searchMangaUrlSelector = "h2.z8x-card__title a"

    override fun parseGenres(document: Document): List<Genre> = document.select("label.r9-chip:has(input[type=checkbox][name='genre[]'])").map {
        Genre(
            it.selectFirst("span")!!.text(),
            it.selectFirst("input")!!.`val`(),
        )
    }

    override val mangaDetailsSelectorTitle = "h1.nbu-hero__title, h1#nbu-hero-title"
    override val mangaDetailsSelectorThumbnail = "img.nbu-hero__img"
    override val mangaDetailsSelectorDescription = ".nbu-summary__body"

    override fun chapterListSelector() = "a.nxv3-card:not(.zax-chapter-premium)"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst(".zax-chapter-title")!!.text()
    }
}
