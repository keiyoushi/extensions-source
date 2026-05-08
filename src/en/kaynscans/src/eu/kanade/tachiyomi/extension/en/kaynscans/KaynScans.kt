package eu.kanade.tachiyomi.extension.en.kaynscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.util.Locale

class KaynScans :
    Keyoapp(
        "Kayn Scans",
        "https://kaynscan.com",
        "en",
    ) {
    // The Trending row on the homepage only has six entries, so we point Popular
    // at the full /series/ catalogue instead. The site doesn't expose any sort
    // parameters, so the default ordering (recently added) is what users get.
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/", headers)

    override fun popularMangaSelector(): String = "#searched_series_page > button"

    // Filters
    //
    // The site filters client-side, so we always fetch /series/ and narrow the
    // returned cards using their `tags`, `data-status`, and `data-type`
    // attributes. Status and Type are multi-select to mirror the site UX.

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("")
            if (query.isNotBlank()) addQueryParameter("q", query)
            filters.forEach { filter ->
                when (filter) {
                    is Keyoapp.GenreList ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("genre", it.id) }
                    is StatusFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("status", (it as ValueCheckBox).id) }
                    is TypeFilter ->
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("type", (it as ValueCheckBox).id) }
                    else -> Unit
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        val document = response.asJsoup()

        val url = response.request.url
        val query = url.queryParameter("q").orEmpty()
        val genres = url.queryParameterValues("genre")
        val statuses = url.queryParameterValues("status").mapNotNull { it?.lowercase(Locale.ENGLISH) }
        val types = url.queryParameterValues("type").mapNotNull { it?.lowercase(Locale.ENGLISH) }

        val mangaList = document.select(searchMangaSelector())
            .filter { it.attr("title").contains(query, true) }
            .filter { entry ->
                if (genres.isEmpty()) return@filter true
                val entryTags = runCatching { entry.attr("tags").parseAs<List<String>>() }
                    .getOrDefault(emptyList())
                genres.all { g -> entryTags.any { it.equals(g, true) } }
            }
            .filter { statuses.isEmpty() || statuses.contains(it.attr("data-status").lowercase(Locale.ENGLISH)) }
            .filter { types.isEmpty() || types.contains(it.attr("data-type").lowercase(Locale.ENGLISH)) }
            .map(::searchMangaFromElement)

        return MangasPage(mangaList, false)
    }

    override fun getFilterList(): FilterList {
        // Build CheckBox instances fresh each call — sharing them across filter
        // creations breaks the "Reset" button because they keep their state.
        val parentFilters = super.getFilterList().toList()
        return FilterList(
            listOf<Filter<*>>(
                StatusFilter(STATUS_VALUES.map { ValueCheckBox(it.first, it.second) }),
                TypeFilter(TYPE_VALUES.map { ValueCheckBox(it.first, it.second) }),
                Filter.Separator(),
            ) + parentFilters,
        )
    }

    // The site has no curated genre list anywhere on the page, so we derive one
    // from the catalogue itself: the union of every card's `tags` JSON array,
    // deduplicated case-insensitively (so `FULL COLOR` and `full color` collapse
    // into one entry).
    override fun parseGenres(document: Document): List<Keyoapp.Genre> {
        val canonical = linkedMapOf<String, String>()

        document.select("#searched_series_page button[tags]").forEach { btn ->
            val tags = runCatching { btn.attr("tags").parseAs<List<String>>() }
                .getOrDefault(emptyList())
            for (raw in tags) {
                val cleaned = raw.trim()
                if (cleaned.isEmpty()) continue
                val key = cleaned.lowercase(Locale.ENGLISH)
                if (key !in canonical) canonical[key] = cleaned
            }
        }

        return canonical.values
            .sortedBy { it.lowercase(Locale.ENGLISH) }
            .map { Keyoapp.Genre(it) }
    }

    // The site reworked the manga page; legacy `div[alt=...]` selectors no
    // longer match. Each label is now a `<span>` inside a header div, with the
    // value in the sibling div below it. Genres became `<a>` links pointing at
    // `/series/?genre=…`.
    override val descriptionSelector: String = "p[style*='pre-wrap']"
    override val statusSelector: String = "div:has(> span:contains(Status)) + div"
    override val authorSelector: String = "div:has(> span:contains(Author)) + div"
    override val artistSelector: String = "div:has(> span:contains(Artist)) + div"
    override val typeSelector: String = "div:has(> span:contains(Type)) + div"
    override val genreSelector: String = "a[href*='/series/?genre=']"

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        // Alternative titles live in a sibling block right after a label whose
        // text is exactly "Alternative titles". A pure CSS approach is fragile
        // because the label is wrapped in whitespace, so we look it up by text.
        val altLabel = document.select("div.font-medium")
            .firstOrNull { it.text() == "Alternative titles" }
        val altTitles = altLabel?.nextElementSibling()
            ?.select("span")
            ?.map { it.text() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (altTitles.isNotEmpty()) {
            val altBlock = "Alternative titles:\n" + altTitles.joinToString("\n")
            description = listOfNotNull(description?.takeIf(String::isNotEmpty), altBlock)
                .joinToString("\n\n")
        }
    }

    private class ValueCheckBox(name: String, val id: String) : Filter.CheckBox(name)

    private class StatusFilter(options: List<ValueCheckBox>) : Filter.Group<ValueCheckBox>("Status", options)

    private class TypeFilter(options: List<ValueCheckBox>) : Filter.Group<ValueCheckBox>("Type", options)

    companion object {
        private val STATUS_VALUES = listOf(
            "Ongoing" to "ongoing",
            "Completed" to "completed",
            "Hiatus" to "hiatus",
            "Paused" to "paused",
            "Dropped" to "dropped",
        )
        private val TYPE_VALUES = listOf(
            "Manga" to "manga",
            "Manhua" to "manhua",
            "Manhwa" to "manhwa",
            "Mangatoon" to "mangatoon",
        )
    }
}
