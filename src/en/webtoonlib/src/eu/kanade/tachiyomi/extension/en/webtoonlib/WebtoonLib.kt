package eu.kanade.tachiyomi.extension.en.webtoonlib

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getTurnstileToken
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class WebtoonLib :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val hideAdult get() = preferences.getBoolean(PREF_HIDE_ADULT, false)

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(BubbleInterceptor())
        rateLimit(2) { it.host == API_HOST }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchSeries(browseUrl(page, "popularityAll"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchSeries(browseUrl(page, "latestChapter"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = seriesUrl(page, "popularityAll").newBuilder().apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
        }.build()
        return if (query.isBlank()) fetchSeries(url) else searchSeries(url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "series" || segments[1].isEmpty()) return null
        return getSeries(segments[1]).toSManga().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = getSeries(manga.url)
        return SMangaUpdate(
            series.toSManga(),
            if (fetchChapters) getChapters(manga.url, series) else chapters,
        )
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("webtoons/slug")
            .addPathSegment(manga.url)
            .addPathSegment("related")
            .addQueryParameter("limit", "12")
            .build()
        return client.get(url).parseAs<List<Series>>()
            .filterNot { hideAdult && it.isAdult }
            .map { it.toSManga() }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (slug, key) = chapter.url.split("/", limit = 2)
        return "$baseUrl/series/$slug/chapter-$key"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("chapters/by-slug")
            .addPathSegments(chapter.url)
            .addQueryParameter("languageCode", "en")
            .build()
        val reader = client.get(url).parseAs<Reader>()
        if (reader.chapterType != "SOFTSUB") {
            return reader.pages.mapIndexed { index, image -> Page(index, imageUrl = image) }
        }

        // Originals carry the lettering when the chapter was uploaded in English
        val manifest = client.get(reader.manifestUrl!!).parseAs<Manifest>()
        return manifest.pages.mapIndexed { index, page ->
            val image = if (manifest.sourceLanguage == "en" || page.bubbles.isEmpty()) {
                page.originalImageUrl
            } else {
                page.cleanImageUrl.toHttpUrl().newBuilder().fragment(page.bubbles.toJsonString()).toString()
            }
            Page(index, imageUrl = image)
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/series").asJsoup()
        fun options(section: String, param: String) = document.select("section[aria-labelledby$=-$section] a[href*='?$param=']").map {
            FilterOption(it.text(), it.absUrl("href").toHttpUrl().queryParameter(param)!!)
        }
        return FilterData(
            options("type", "type"),
            options("status", "status"),
            options("genres", "genre"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        buildList {
            add(SortFilter())
            add(ContentTypeFilter())
            data?.parseAs<FilterData>()?.let {
                if (it.types.isNotEmpty()) add(FetchedFilter("Type", "type", it.types))
                if (it.statuses.isNotEmpty()) add(FetchedFilter("Status", "status", it.statuses))
                if (it.genres.isNotEmpty()) add(FetchedFilter("Category", "genre", it.genres))
            }
            add(ExcludeAdultFilter(hideAdult))
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_ADULT
            title = "Hide adult series"
            summary = "Also the default for the search filter"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun browseUrl(page: Int, sort: String): HttpUrl = seriesUrl(page, sort).newBuilder().apply {
        if (hideAdult) addQueryParameter("excludeAdult", "true")
    }.build()

    private fun seriesUrl(page: Int, sort: String): HttpUrl = API_URL.toHttpUrl().newBuilder()
        .addPathSegment("webtoons")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("pageSize", PAGE_SIZE.toString())
        .addQueryParameter("sort", sort)
        .build()

    private suspend fun fetchSeries(url: HttpUrl): MangasPage {
        val list = client.get(url).parseAs<SeriesList>()
        val page = url.queryParameter("page")!!.toInt()
        return MangasPage(list.items.map { it.toSManga() }, page * PAGE_SIZE < list.total)
    }

    private suspend fun searchSeries(url: HttpUrl): MangasPage = try {
        try {
            fetchSeries(url)
        } catch (e: HttpException) {
            if (e.code != 403) throw e
            openSearch()
            fetchSeries(url)
        }
    } catch (e: HttpException) {
        if (e.code != 429 && e.code != 503) throw e
        searchCatalogue(url)
    }

    private suspend fun openSearch() {
        val access = client.get("$API_URL/search/access").parseAs<SearchAccess>()
        if (access.challengeRequired) {
            val token = getTurnstileToken(baseUrl, access.siteKey, action = "reader-search")
            client.post("$API_URL/search/access/verify", body = SearchVerification(token).toJsonRequestBody()).close()
        }
    }

    // The daily search limit doesn't cover the listing, which takes the same filters
    private suspend fun searchCatalogue(url: HttpUrl): MangasPage {
        if (url.queryParameter("page") != "1") return MangasPage(emptyList(), false)

        val query = url.queryParameter("q")!!
        val mangas = mutableListOf<SManga>()
        var page = 1
        do {
            val pageUrl = url.newBuilder()
                .removeAllQueryParameters("q")
                .setQueryParameter("page", page.toString())
                .setQueryParameter("pageSize", MAX_PAGE_SIZE.toString())
                .build()
            val list = client.get(pageUrl).parseAs<SeriesList>()
            list.items.map { it.toSManga() }.filterTo(mangas) { it.title.contains(query, ignoreCase = true) }
        } while (page++ * MAX_PAGE_SIZE < list.total)
        return MangasPage(mangas, false)
    }

    private suspend fun getSeries(slug: String): Series {
        val url = API_URL.toHttpUrl().newBuilder()
            .addPathSegments("webtoons/slug")
            .addPathSegment(slug)
            .build()
        return client.get(url).parseAs()
    }

    private suspend fun getChapters(slug: String, series: Series): List<SChapter> {
        val group = series.publisherGroup?.name
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val url = API_URL.toHttpUrl().newBuilder()
                .addPathSegments("chapters/webtoons")
                .addPathSegment(series.id)
                .addPathSegments("chapters/paginated")
                .addQueryParameter("sort", "numberDesc")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("pageSize", MAX_PAGE_SIZE.toString())
                .addQueryParameter("languageCode", "en")
                .build()
            val list = client.get(url).parseAs<ChapterList>()
            list.items.mapTo(chapters) { it.toSChapter(slug, group) }
        } while (page++ < list.totalPages)
        return chapters
    }

    companion object {
        private const val API_HOST = "api.webtoonlib.to"
        private const val API_URL = "https://$API_HOST"
        private const val PAGE_SIZE = 40
        private const val MAX_PAGE_SIZE = 100
        private const val PREF_HIDE_ADULT = "hide_adult"
    }
}
