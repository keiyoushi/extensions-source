package eu.kanade.tachiyomi.extension.tr.seitoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class Seitoon : KeiSource() {

    override val supportsFilterFetching = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(AssetTokenInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage = seriesPage(page, seriesUrl().addQueryParameter("sort", "popular").build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesPage(page, seriesUrl().addQueryParameter("sort", "recently_updated").build())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = seriesUrl().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach { it.addTo(this) }
        }.build()
        return seriesPage(page, url)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] !in SERIES_PATHS || segments[1].isEmpty()) return null

        val manga = SManga.create().apply { this.url = "/comics/${segments[1]}" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.substringAfterLast('/')
        val from = Fetch.XHR.from(getMangaUrl(manga))

        return SMangaUpdate(
            manga = { if (fetchDetails) client.get(seriesUrl(slug), from).parseAs<SeriesResponse>().data.series.toSMangaDetails(baseUrl.toHttpUrl()) else manga },
            chapters = { if (fetchChapters) fetchChapters(slug, from).map { it.toSChapter() } else chapters },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.memo["locked"]?.booleanOrNull == true) return emptyList()

        val chapterUrl = getChapterUrl(chapter)
        val id = chapter.memo["id"]?.stringOrNull ?: chapterId(chapter)
        val url = apiUrl.newBuilder()
            .addPathSegments("reader/chapters")
            .addPathSegment(id)
            .addPathSegment("manifest")
            .build()
        return client.get(url, Fetch.XHR.from(chapterUrl)).parseAs<ManifestResponse>().data.manifest.pages
            .mapIndexed { index, page ->
                Page(index, url = "$chapterUrl#${page.deliveryToken}", imageUrl = url.resolve(page.imageUrl)!!.toString())
            }
    }

    override fun getImageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(headers)
        .header("Accept", "image/*")
        .header(ASSET_TOKEN_HEADER, page.url.substringAfter('#'))
        .fetch(Fetch.XHR.from(page.url.substringBefore('#')))
        .build()

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/browse", Fetch.DOCUMENT).asJsoup()
        return document.select("form[data-browse-filter-form] select[name]").map { select ->
            FilterDto(
                name = select.attr("name"),
                label = document.selectFirst("label[for=${select.id()}]")?.text() ?: select.attr("name"),
                options = select.select("option")
                    .filterNot { it.attr("value") == "novel" }
                    .map { OptionDto(it.text().replace(COUNT_REGEX, ""), it.attr("value")) },
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<FilterDto>>())

    // The list pages with a cursor given by the page before it
    @Volatile
    private var nextCursor: Pair<String, String>? = null

    private suspend fun seriesPage(page: Int, url: HttpUrl): MangasPage {
        val pageUrl = if (page == 1) {
            url
        } else {
            val cursor = nextCursor?.takeIf { it.first == "$url#$page" }?.second ?: return MangasPage(emptyList(), false)
            url.newBuilder().addQueryParameter("cursor", cursor).build()
        }
        val result = client.get(pageUrl, Fetch.XHR.from("$baseUrl/browse")).parseAs<SeriesListResponse>()
        val next = result.meta.cursor?.takeIf { result.meta.hasMore }
        nextCursor = next?.let { "$url#${page + 1}" to it }
        val series = result.data.series
            .filterNot { it.contentType == "novel" }
            .map { it.toSManga(baseUrl.toHttpUrl()) }
        return MangasPage(series, next != null)
    }

    private suspend fun fetchChapters(slug: String, from: Fetch): List<ChapterDto> {
        val chapters = mutableListOf<ChapterDto>()
        var cursor: String? = null
        do {
            val url = seriesUrl(slug).newBuilder()
                .addPathSegment("chapters")
                .addQueryParameter("limit", PAGE_SIZE)
                .apply { cursor?.let { addQueryParameter("cursor", it) } }
                .build()
            val result = client.get(url, from).parseAs<ChapterListResponse>()
            chapters += result.data.chapters
            cursor = result.meta.cursor?.takeIf { result.meta.hasMore }
        } while (cursor != null)
        // Oldest first from the API, newest first on the site
        return chapters.asReversed()
    }

    // Chapters saved without their id
    private suspend fun chapterId(chapter: SChapter): String {
        val segments = chapter.url.split('/')
        return fetchChapters(segments[2], Fetch.XHR.from(getChapterUrl(chapter))).first { it.slug == segments[4] }.id
    }

    private fun seriesUrl(): HttpUrl.Builder = apiUrl.newBuilder()
        .addPathSegments("catalog/series")
        .addQueryParameter("limit", PAGE_SIZE)

    private fun seriesUrl(slug: String): HttpUrl = apiUrl.newBuilder()
        .addPathSegments("catalog/series")
        .addPathSegment(slug)
        .build()

    private val apiUrl: HttpUrl
        get() = baseUrl.toHttpUrl().newBuilder().addPathSegments("api/v1").build()

    companion object {
        private const val PAGE_SIZE = "100"
        private val SERIES_PATHS = setOf("comics", "series")
        private val COUNT_REGEX = """\s*\(\d+\)$""".toRegex()
    }
}
