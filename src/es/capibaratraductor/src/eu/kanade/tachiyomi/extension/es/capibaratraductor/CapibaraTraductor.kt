package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Clock

@Source
abstract class CapibaraTraductor : KeiSource() {

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3) { it.host == baseUrlHost }
        addInterceptor(::authorizationInterceptor)
    }

    private fun authorizationInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val token = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
            .firstOrNull { it.name == "token" }

        if (token == null) return chain.proceed(request)

        val newRequest = request.newBuilder().apply {
            header("Authorization", "Bearer ${token.value}")
        }.build()

        return chain.proceed(newRequest)
    }

    private fun getScanHeaders(organizationSlug: String): Headers = headers.newBuilder()
        .add("x-organization", organizationSlug)
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(mangaListUrl(page).addQueryParameter("order", "popular").build(), page, headers)

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(mangaListUrl(page).addQueryParameter("order", "latest").build(), page, headers)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = mangaListUrl(page)
        val headers = headers.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> url.setQueryParameter("order", filter.toUriPart())
                is TypeFilter -> url.setQueryParameter("type", filter.toUriPart())
                is ScanlatorFilter -> if (filter.state != 0) headers["x-organization"] = filter.toUriPart()
                else -> {}
            }
        }

        if (query.isNotBlank()) url.setQueryParameter("search", query)

        return parseMangaList(url.build(), page, headers.build())
    }

    private fun mangaListUrl(page: Int) = "$baseUrl/api/manga-custom".toHttpUrl().newBuilder()
        .addQueryParameter("page", page.toString())
        .addQueryParameter("limit", PAGE_LIMIT.toString())
        .addQueryParameter("type", COMIC_TYPES)

    private suspend fun parseMangaList(url: HttpUrl, page: Int, headers: Headers): MangasPage {
        val result = client.get(url, headers).parseAs<Data<SeriesListDataDto>>()

        val mangas = result.data.series.map { it.toSManga() }
        val hasNextPage = page < result.data.maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val sfwScans = fetchAllScans(includeNsfw = false)
        val nsfwScans = fetchAllScans(includeNsfw = true)

        return buildList {
            add("Todos" to "")
            addAll(
                (sfwScans + nsfwScans)
                    .distinctBy { it.second }
                    .sortedBy { it.first },
            )
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        data?.parseAs<List<Pair<String, String>>>()?.also {
            filters.add(ScanlatorFilter("Scanlator", it.toTypedArray()))
        }

        filters.add(TypeFilter("Tipo", getTypeList()))
        filters.add(SortByFilter("Ordenar por", getSortList()))

        return FilterList(filters)
    }

    private fun getSortList() = arrayOf(
        "Popularidad" to "popular",
        "Recientes" to "latest",
        "A-Z" to "alphabetical",
    )

    private fun getTypeList() = arrayOf(
        "Todos" to COMIC_TYPES,
        "Manga" to "manga",
        "Manhwa" to "manhwa",
        "Manhua" to "manhua",
    )

    private suspend fun fetchAllScans(includeNsfw: Boolean): List<Pair<String, String>> {
        val scans = mutableListOf<Pair<String, String>>()
        var page = 1

        while (true) {
            val url = "$baseUrl/api/landing/scans".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "name")
                .addQueryParameter("limit", "100")
                .apply {
                    if (includeNsfw) {
                        addQueryParameter("includeNSFW", "true")
                    }
                }
                .build()

            val response = client.get(url)
                .parseAs<Data<ScanListDto>>()
                .data

            scans += response.items.map { it.name to it.id }

            if (!response.hasNextPage()) {
                break
            }

            page++
        }

        return scans
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val segments = url.pathSegments.let { if (it.firstOrNull() == "red") it.drop(1) else it }
        if (segments.size < 3 || segments[1] != "manga" || segments[2].isEmpty()) return null
        val seriesSlug = segments[2]
        val manga = SManga.create().apply {
            if (segments[0] == "joint") {
                val organizationSlug = client.get("$baseUrl/api/joint/$seriesSlug")
                    .parseAs<Data<SeriesDto>>().data.leaderSlug()
                this.url = "$seriesSlug/$organizationSlug"
                memo = buildJsonObject { put("jointSlug", seriesSlug) }
            } else {
                this.url = "$seriesSlug/${segments[0]}"
            }
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        return "$baseUrl/$organizationSlug/manga/$seriesSlug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        val seriesPath = if (manga.memo["jointSlug"] != null) "joint" else "manga-custom"
        val result = client.get("$baseUrl/api/$seriesPath/$seriesSlug", getScanHeaders(organizationSlug))
            .parseAs<Data<SeriesDto>>()
            .data

        val now = Clock.System.now().toEpochMilliseconds()
        val chapterList = result.chapters
            ?.filter { !it.isUnreleased }
            ?.map { it.toSChapter(seriesSlug, organizationSlug) }
            ?.filter { it.date_upload < now }
            ?: emptyList()

        return SMangaUpdate(result.toSMangaDetails(organizationSlug), chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return "$baseUrl/$organizationSlug/manga/$seriesSlug/chapters/$chapterSlug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        val seriesPath = if (chapter.memo["jointId"] != null) "joint" else "manga-custom"

        val result = client.get("$baseUrl/api/$seriesPath/$seriesSlug/chapter/$chapterSlug/pages", getScanHeaders(organizationSlug))
            .parseAs<Data<List<PageDto>>>()
        return result.data.mapIndexed { i, page ->
            Page(i, imageUrl = decodePageUrl(page.pg, result.rv!!, i))
        }
    }

    companion object {
        private const val PAGE_LIMIT = 36
        private const val COMIC_TYPES = "manga,manhwa,manhua"
    }
}
