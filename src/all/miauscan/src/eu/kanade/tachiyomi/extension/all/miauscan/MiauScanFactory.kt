package eu.kanade.tachiyomi.extension.all.miauscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MiauScanFactory : SourceFactory {
    override fun createSources() = listOf(
        MiauScan("es"),
        MiauScan("pt-BR"),
    )
}

open class MiauScan(lang: String) :
    MangaThemesia(
        name = "Miau Scan",
        baseUrl = "https://leemiau.com",
        lang = lang,
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {

    private val portugueseMode =
        if (lang == "pt-BR") Filter.TriState.STATE_INCLUDE else Filter.TriState.STATE_EXCLUDE

    override val seriesGenreSelector = ".mgen a:not(:contains(Português))"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genreFilterIndex = filters.indexOfFirst { it is GenreListFilter }
        val genreFilter = filters.getOrNull(genreFilterIndex) as? GenreListFilter
            ?: GenreListFilter("", emptyList())

        val overloadedGenreFilter = GenreListFilter(
            genreFilter.name,
            genreFilter.state + listOf(
                Genre("", PORTUGUESE_GENRE_ID, portugueseMode),
            ),
        )

        val overloadedFilters = filters.toMutableList().apply {
            if (genreFilterIndex != -1) {
                removeAt(genreFilterIndex)
            }

            add(overloadedGenreFilter)
        }

        return super.searchMangaRequest(page, query, FilterList(overloadedFilters))
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

    override fun pageListParse(document: Document): List<Page> {
        countViews(document)

        return document.select(pageSelector)
            .filterNot { it.imgAttr().isEmpty() }
            .mapIndexed { i, img -> Page(i, document.location(), img.imgAttr()) }
    }

    override fun Element.imgAttr(): String = when {
        hasAttr("data-lm-orig-src") -> attr("abs:data-lm-orig-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    override fun getGenreList(): List<Genre> = super.getGenreList().filter { it.value != PORTUGUESE_GENRE_ID }

    companion object {
        const val PORTUGUESE_GENRE_ID = "307"

        val PORTUGUESE_SUFFIX = "^\\(\\s*Portugu[êe]s\\s*\\)\\s*".toRegex(RegexOption.IGNORE_CASE)
    }
}
