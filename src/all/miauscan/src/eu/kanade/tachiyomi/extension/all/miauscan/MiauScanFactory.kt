package eu.kanade.tachiyomi.extension.all.miauscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
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

open class MiauScan(lang: String) : MangaThemesia(
    name = "Miau Scan",
    baseUrl = "https://zonamiau.com",
    lang = lang,
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
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

    override fun searchMangaFromElement(element: Element): SManga {
        return super.searchMangaFromElement(element).apply {
            title = title.replace(PORTUGUESE_SUFFIX, "")
        }
    }

    override val seriesAuthorSelector = ".tsinfo .imptdt:contains(autor) i"
    override val seriesStatusSelector = ".tsinfo .imptdt:contains(estado) i"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            title = title.replace(PORTUGUESE_SUFFIX, "")
        }
    }

    override fun getGenreList(): List<Genre> {
        return super.getGenreList().filter { it.value != PORTUGUESE_GENRE_ID }
    }

    companion object {
        const val PORTUGUESE_GENRE_ID = "307"

        val PORTUGUESE_SUFFIX = "^\\(\\s*Portugu[êe]s\\s*\\)\\s*".toRegex(RegexOption.IGNORE_CASE)
    }
}
