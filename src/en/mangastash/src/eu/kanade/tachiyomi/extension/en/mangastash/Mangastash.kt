package eu.kanade.tachiyomi.extension.en.mangastash

import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class Mangastash :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)
        .addCookie { listOfNotNull(contentFilterCookie()) }

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page) { addQueryParameter("sort", "popular-month") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page) { addQueryParameter("sort", "latest") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val list = filters.firstInstanceOrNull<ListFilter>()?.path
            if (list != null) {
                val url = baseUrl.toHttpUrl().newBuilder().addEncodedPathSegments(list).addPathSegment("").build()
                val mangas = client.get(url, Fetch.DOCUMENT).asJsoup().select(".series-grid a.series-card").map { it.toSManga() }
                return MangasPage(mangas, false)
            }
            return browse(page) { filters.filterIsInstance<QueryFilter>().forEach { it.addToUrl(this) } }
        }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("q", query)
            .addPage(page)
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage("a.search-result-row")
    }

    private suspend fun browse(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("browse")
            .addPathSegment("")
            .apply(params)
            .addPage(page)
            .build()
        return client.get(url, Fetch.DOCUMENT).asJsoup().toMangasPage(".series-grid a.series-card")
    }

    private fun HttpUrl.Builder.addPage(page: Int) = apply {
        if (page > 1) addQueryParameter("page", page.toString())
    }

    private fun Document.toMangasPage(cardSelector: String) = MangasPage(
        select(cardSelector).map { it.toSManga() },
        selectFirst(".pagination a.page-next") != null,
    )

    private fun Element.toSManga() = SManga.create().apply {
        url = slug()
        title = selectFirst("h3")!!.text()
        thumbnail_url = selectFirst("img")?.absUrl("src")
        status = select(".badge").firstNotNullOfOrNull { STATUSES[it.text().lowercase()] } ?: SManga.UNKNOWN
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = segments.firstOrNull() ?: return null
        if (segments.size > 2 || segments.getOrNull(1)?.startsWith("chapter-") == false) return null
        val manga = SManga.create().apply { this.url = slug }
        val page = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst(".series-page") ?: return null
        return manga.apply {
            title = page.selectFirst(".series-meta h1")!!.text()
            thumbnail_url = page.selectFirst(".series-cover img")?.coverUrl()
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val page = document.selectFirst(".series-page")!!

        manga.apply {
            title = page.selectFirst(".series-meta h1")!!.text()
            thumbnail_url = page.selectFirst(".series-cover img")?.coverUrl()
            val credits = page.selectFirst(".series-author")?.text()?.split(" · ").orEmpty()
            author = credits.firstOrNull { it.startsWith("by ") }?.removePrefix("by ")
            artist = credits.firstOrNull { it.startsWith("Art by ") }?.removePrefix("Art by ")
            val badges = page.select(".series-badges .badge:not(.lang-badge)").map { it.text() }
            status = badges.firstNotNullOfOrNull { STATUSES[it.lowercase()] } ?: SManga.UNKNOWN
            genre = page.select(".series-tags .tag-group")
                .sortedBy { TAG_GROUP_ORDER.indexOf(it.selectFirst(".tag-category")?.text()).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .flatMap { group -> group.select("a.tag-chip").map { it.text() } }
                .distinct()
                .joinToString()
                .ifEmpty { null }
            val rating = page.selectFirst(".rating-average")?.text()?.let(RATING_REGEX::find)
            description = MarkdownDescription.Builder()
                .rating(rating?.groupValues?.get(1)?.toDoubleOrNull(), max = 5, votes = rating?.groupValues?.get(2)?.toIntOrNull())
                .info(*(badges.filter { it.lowercase() !in STATUSES } + page.select(".series-rankings span").map { it.text() }).toTypedArray())
                .description(page.selectFirst(".series-description"))
                .altTitles(listOfNotNull(page.selectFirst(".series-title-romaji")?.textOrNull()))
                .build()
        }

        val chapterList = page.select("#chapter-list a.chapter-row").map { row ->
            SChapter.create().apply {
                setUrlWithoutDomain(row.absUrl("href"))
                name = buildString {
                    append(row.selectFirst(".chapter-num")!!.text())
                    row.selectFirst(".chapter-title")?.textOrNull()?.let { append(" - ", it) }
                }
                chapter_number = row.attrOrNull("data-num")?.toFloatOrNull() ?: -1f
            }
        }.asReversed()

        return SMangaUpdate(manga, chapterList)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".similar-series-grid a.series-card")
        .map { it.toSManga() }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("#reader-pages .reader-page img[data-src]")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("data-src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("browse").addPathSegment("").build()
        val collectionsUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment("collections").addPathSegment("").build()
        val collections = async {
            client.get(collectionsUrl, Fetch.DOCUMENT).asJsoup().select("a.collection-card").map { card ->
                FilterOption(card.selectFirst("h3")!!.text(), card.absUrl("href").toHttpUrl().encodedPath.trim('/'))
            }
        }
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val sorts = document.select("select.sort-select option").map { option ->
            FilterOption(option.text(), url.resolve(option.attr("value"))!!.queryParameter("sort")!!)
        }
        val sections = document.select("#browse-form .filter-section").mapNotNull { section ->
            val inputs = section.select("input[name]")
            // Language has English only
            if (inputs.size < 2) return@mapNotNull null
            FilterSection(
                name = section.selectFirst("h3")!!.text(),
                param = section.selectFirst("input[name]")!!.attr("name"),
                options = inputs.map { FilterOption(it.parent()!!.text(), it.attr("value")) },
            )
        }
        FilterData(listOf(FilterOption("Trending Now", "trending")) + collections.await(), sorts, sections).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        val sorts = filterData.sorts
        return FilterList(
            listOf(
                ListFilter(filterData.lists),
                Filter.Header("Trending and collections ignore the filters below"),
                Filter.Separator(),
                SelectFilter("Sort", "sort", sorts, sorts.indexOfFirst { it.value == "popular-month" }.coerceAtLeast(0)),
            ) +
                filterData.sections.map { section ->
                    if (section.param.endsWith("[]")) {
                        CheckBoxGroup(section.name, section.param, section.options)
                    } else {
                        SelectFilter(section.name, section.param, section.options)
                    }
                },
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(manga.url)
        .addPathSegment("")
        .build()
        .toString()

    private fun Element.slug(): String = absUrl("href").toHttpUrl().pathSegments.first()

    // Full-size cover next to the thumbnails folder the pages link
    private fun Element.coverUrl(): String {
        val url = absUrl("src").toHttpUrl()
        val thumbnails = url.pathSegments.indexOf("thumbnails")
        return if (thumbnails < 0) url.toString() else url.newBuilder().removePathSegment(thumbnails).build().toString()
    }

    private fun contentFilterCookie(): Pair<String, String>? {
        val level = preferences.getString(PREF_RATING, RATING_ALL)!!
        val hidden = HIDE_PREFS.map { preferences.getStringSet(it.key, emptySet())!!.sorted() }
        if (level == RATING_ALL && hidden.all { it.isEmpty() }) return null
        return CONTENT_FILTER_COOKIE to (listOf(level) + hidden.map { it.joinToString("~") }).joinToString("|")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_RATING
            title = "Content rating"
            entries = RATINGS.map { it.first }.toTypedArray()
            entryValues = RATINGS.map { it.second }.toTypedArray()
            setDefaultValue(RATING_ALL)
            summary = "%s"
        }.also(screen::addPreference)

        HIDE_PREFS.forEach { pref ->
            MultiSelectListPreference(screen.context).apply {
                key = pref.key
                title = pref.title
                summary = "Left out of popular, latest and search"
                entries = pref.options.map { it.first }.toTypedArray()
                entryValues = pref.options.map { it.second }.toTypedArray()
                setDefaultValue(emptySet<String>())
            }.also(screen::addPreference)
        }
    }

    companion object {
        private val STATUSES = mapOf(
            "ongoing" to SManga.ONGOING,
            "completed" to SManga.COMPLETED,
            "hiatus" to SManga.ON_HIATUS,
            "cancelled" to SManga.CANCELLED,
        )

        // Type and demographic first, then the site's tag groups
        private val TAG_GROUP_ORDER = listOf("Formats:", "Demographic:", "Genres:", "Themes:", "Moods:")

        private val RATING_REGEX = """([\d.]+) ★ \((\d+) ratings?\)""".toRegex()

        private const val CONTENT_FILTER_COOKIE = "manga_reader_v2_cf"
        private const val PREF_RATING = "content_rating"
        private const val RATING_ALL = "all"

        // The site's content filter panel
        private val RATINGS = listOf(
            "Show all" to RATING_ALL,
            "Hide mature" to "no_mature",
            "Safe only" to "safe_only",
        )

        // Cookie order: level|genres|formats|demographics
        private val HIDE_PREFS = listOf(
            HidePreference(
                "hide_genres",
                "Hidden genres",
                listOf(
                    "Action" to "action", "Adventure" to "adventure", "Comedy" to "comedy", "Drama" to "drama",
                    "Fantasy" to "fantasy", "Horror" to "horror", "Isekai" to "isekai", "Mystery" to "mystery",
                    "Romance" to "romance", "School Life" to "school-life", "Sci-Fi" to "sci-fi",
                    "Slice of Life" to "slice-of-life", "Sports" to "sports", "Thriller" to "thriller",
                ),
            ),
            HidePreference(
                "hide_formats",
                "Hidden formats",
                listOf(
                    "4-Koma" to "4-koma",
                    "Manga" to "manga",
                    "Manhua" to "manhua",
                    "Manhwa" to "manhwa",
                    "One-shot" to "one-shot",
                    "Webtoon" to "webtoon",
                ),
            ),
            HidePreference(
                "hide_demographics",
                "Hidden demographics",
                listOf("Josei" to "josei", "Seinen" to "seinen", "Shoujo" to "shoujo", "Shounen" to "shounen"),
            ),
        )
    }
}

private class HidePreference(val key: String, val title: String, val options: List<Pair<String, String>>)
