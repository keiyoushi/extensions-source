package eu.kanade.tachiyomi.extension.all.miauscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class MiauScan : MangaThemesia() {
    override val datePattern = "dd/MM/yyyy"

    override val seriesGenreSelector = ".mgen a:not(:contains(Português))"

    override fun searchMangaUrl(page: Int, query: String) = super.searchMangaUrl(page, query).apply {
        if (lang == "pt-BR") addQueryParameter("genre[]", PORTUGUESE_GENRE_ID)
    }

    override fun searchMangaFromElement(element: Element): SManga = super.searchMangaFromElement(element).apply {
        title = title.replace(PORTUGUESE_SUFFIX, "")
    }

    override val seriesStatusSelector = ".lm4-poster-status"
    override val seriesThumbnailSelector = "img.lm4-poster-image"
    override val seriesDescriptionSelector = ".lm4-summary-full"
    private val altDescriptionSelector = ".lm4-summary-short"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        title = title.replace(PORTUGUESE_SUFFIX, "")
        if (description.isNullOrBlank()) {
            description = document.selectFirst(altDescriptionSelector)?.text().orEmpty()
        }
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        val chTitle = element.select(".lm4-chapter-name").text()
        val chSubtitle = element.select(".lm4-chapter-subtitle").text()
        name = buildString {
            append(chTitle)
            if (chSubtitle.isNotEmpty() && chSubtitle != chTitle) {
                append(" - ")
                append(chSubtitle)
            }
        }
        date_upload = element.selectFirst(".lm4-chapter-date")?.text().parseChapterDate()
    }

    override fun Element.imgAttr(): String = when {
        hasAttr("data-lm-orig-src") -> attr("abs:data-lm-orig-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun parseGenres(document: Document) = super.parseGenres(document)?.filter { it.value != PORTUGUESE_GENRE_ID }

    companion object {
        const val PORTUGUESE_GENRE_ID = "307"

        val PORTUGUESE_SUFFIX = "^\\(\\s*Portugu[êe]s\\s*\\)\\s*".toRegex(RegexOption.IGNORE_CASE)
    }
}
