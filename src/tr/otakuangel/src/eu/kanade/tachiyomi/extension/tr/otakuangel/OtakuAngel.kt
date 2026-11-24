package eu.kanade.tachiyomi.extension.tr.otakuangel

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class OtakuAngel : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    override suspend fun getPopularManga(page: Int) = fetchPopular(listDataUrl("populer", page))

    // The home page lists the series of the latest chapters
    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/__data.json?chaptersPage=$page", Fetch.XHR.from("/"))
        .parseSvelteData()!!
        .parseAs<HomeDto>()
        .toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = listDataUrl("kesfet", page).newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("ara", query.trim())
            filters.filterIsInstance<QueryFilter>().forEach { filter ->
                filter.toQuery()?.let { addQueryParameter(filter.param, it) }
            }
        }.build()
        return fetchExplore(url)
    }

    // Explore and popular links keep their filters, period included
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val list = url.pathSegments.singleOrNull()
        if (url.host.removePrefix("www.") != host || list !in setOf("kesfet", "populer")) return super.getMangasByUrl(url, page)
        val dataUrl = listDataUrl(list!!, page).newBuilder().apply {
            url.queryParameterNames.filter { it != "sayfa" }.forEach { name ->
                url.queryParameterValues(name).forEach { addQueryParameter(name, it) }
            }
        }.build()
        return if (list == "kesfet") fetchExplore(dataUrl) else fetchPopular(dataUrl)
    }

    private fun listDataUrl(list: String, page: Int) = "$baseUrl/$list/__data.json?sayfa=$page".toHttpUrl()

    private suspend fun fetchPopular(url: HttpUrl) = client.get(url, Fetch.XHR.from("/populer"))
        .parseSvelteData()!!
        .parseAs<PopularDto>()
        .toMangasPage()

    private suspend fun fetchExplore(url: HttpUrl) = client.get(url, Fetch.XHR.from("/kesfet"))
        .parseSvelteData()!!
        .parseAs<ExploreDto>()
        .toMangasPage()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host.removePrefix("www.") != host || segments.size < 2 || segments[0] != "manga") return null
        // Series links are /manga/<id>-<slug>, and the site finds a series by its id alone
        val id = segments[1].substringBefore('-').takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val manga = fetchSeries(id)?.manga?.takeUnless { it.isNovel } ?: return null
        return manga.toSMangaDetails().apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // The chapter list is a deferred value of the same response
        val series = fetchSeries(manga.url)!!
        return SMangaUpdate(series.manga.toSMangaDetails(), series.chapters.flatMap { it.toSChapters(manga.url) })
    }

    private suspend fun fetchSeries(id: String) = client.get("$baseUrl/manga/$id/__data.json", Fetch.XHR.from("/manga/$id"))
        .parseSvelteData()
        ?.parseAs<SeriesDto>()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter).toHttpUrl()
        val dataUrl = chapterUrl.newBuilder().addPathSegment("__data.json").build()
        val reader = client.get(dataUrl, Fetch.XHR.from(chapterUrl)).parseSvelteData()!!.parseAs<ReaderDto>()
        if (reader.requiresAuth && !reader.isAuthenticated) return emptyList()
        val versionId = chapterUrl.queryParameter("version")
        val version = versionId?.let { id -> reader.versions.first { it.id == id } } ?: reader.versions.first()
        return version.imageUrls.orEmpty().mapIndexed { index, path -> Page(index, imageUrl = "$CDN_URL/$path") }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()
}
