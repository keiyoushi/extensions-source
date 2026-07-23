package eu.kanade.tachiyomi.extension.vi.nhentaiclub

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId

@Source
abstract class NhentaiClub : KeiSource() {

    private val apiUrl = "https://vvcz.online"
    private val imageUrl = "https://vvcz.store"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getApiMangaPage(page, "view")

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getApiMangaPage(page, "recent-update")

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        var sort = "recent-update"
        var status = "all"
        var genres = emptyList<String>()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> genres = filter.selectedValues()
                is SortFilter -> sort = filter.toUriPart()
                is StatusFilter -> status = filter.toUriPart()
                else -> {}
            }
        }

        return getApiMangaPage(
            page = page,
            sort = sort,
            query = query,
            status = status,
            genres = genres,
        )
    }

    private suspend fun getApiMangaPage(
        page: Int,
        sort: String,
        query: String = "",
        status: String = "all",
        genres: List<String> = emptyList(),
    ): MangasPage {
        val url = "$apiUrl/comic/advanced".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("sort", sort)
            .addQueryParameter("status", status)
            .addQueryParameter("includeGenres", genres.joinToString(","))
            .addQueryParameter("excludeGenres", "")
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<AdvancedSearchResponse>()
        val mangas = result.data.map { it.toSManga(imageUrl) }

        return MangasPage(mangas, page * pageSize < result.total)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaId = when (url.pathSegments.firstOrNull()) {
            "g", "read" -> url.pathSegments.getOrNull(1)
            else -> null
        }?.takeIf { it.all(Char::isDigit) } ?: return null

        val manga = SManga.create().apply { setUrlWithoutDomain("/g/$mangaId") }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true).manga
    }

    // ======================== Details + Chapters ==========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.id()
        val data = client.get("$apiUrl/comic/get/$mangaId").parseAs<MangaDetailsDto>()

        return SMangaUpdate(
            manga = data.toSManga(manga.url, imageUrl),
            chapters = data.toSChapterList(baseUrl, language, vietnamZone),
        )
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = (baseUrl + chapter.url).toHttpUrl()
        val mangaId = url.pathSegments.getOrNull(1) ?: return emptyList()
        val chapterName = url.pathSegments.getOrNull(2) ?: return emptyList()
        val pageCount = url.queryParameter("pages")?.toIntOrNull()
            ?: client.get("$apiUrl/comic/get/$mangaId")
                .parseAs<MangaDetailsDto>()
                .pageCount(chapterName)
            ?: return emptyList()

        return (1..pageCount).map { pageNumber ->
            Page(
                index = pageNumber - 1,
                imageUrl = "$imageUrl/$mangaId/$language/$chapterName/$pageNumber.jpg",
            )
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/advanced").asJsoup()
        var genres = emptyList<GenreOption>()

        for (scriptElement in document.select("script[src*=_next/static/chunks/]")) {
            val scriptUrl = scriptElement.absUrl("src").takeIf(String::isNotEmpty) ?: continue
            val script = client.get(scriptUrl).use { it.body.string() }
            genres = genreRegex.findAll(script)
                .map { match -> GenreOption(match.groupValues[1], match.groupValues[2]) }
                .distinctBy { it.slug }
                .toList()
            if (genres.isNotEmpty()) break
        }

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val mangaId = manga.id()
        return client.get("$apiUrl/comic/moreLikeThis/$mangaId")
            .parseAs<List<MangaDto>>()
            .map { it.toSManga(imageUrl) }
    }

    private fun SManga.id(): String = (baseUrl + url).toHttpUrl().pathSegments.getOrNull(1)
        ?: error("Missing manga ID in URL: $url")

    private val pageSize = 24
    private val language = "VI"
    private val vietnamZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val genreRegex = Regex("""\{label:\"([^\"]+)\",href:\"/genre/([^\"]+)\"""")
}
