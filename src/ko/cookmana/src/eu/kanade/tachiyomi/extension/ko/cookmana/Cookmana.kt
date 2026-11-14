package eu.kanade.tachiyomi.extension.ko.cookmana

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException

@Source
abstract class Cookmana : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = getList(page, "/api/popular/list")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getList(page, "/api/lastest/list")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) {
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.state ?: 0
            val path = filters.firstInstance<SortFilter>().selected
            return getList(page, path, genre)
        }
        return search(page, query)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.matches(HOST_REGEX)) return null
        val path = url.pathSegments
        val id = when {
            path.size == 2 && path[0] == "episode" -> path[1]
            path.size == 2 && path[0] == "detail" -> parentEpisodeId(path[1]) ?: return null
            else -> return null
        }
        val manga = SManga.create().apply { this.url = "/episode/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // A /detail/<chapter> link resolves to its episode via the chapter's parentId.
    private suspend fun parentEpisodeId(chapterId: String): String? {
        val detail = client.get("$baseUrl/api/detail/$chapterId".toHttpUrl(), Fetch.XHR).parseAs<DetailDto>()
        return detail.data?.parentId
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val updatedManga = parseMangaDetails(manga, document)
        val chapterList = if (fetchChapters) {
            fetchChapterList(manga, updatedManga.title!!)
        } else {
            chapters
        }
        return SMangaUpdate(updatedManga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast('/')
        val detail = client.get("$baseUrl/api/detail/$id".toHttpUrl(), Fetch.XHR).parseAs<DetailDto>()
        val data = detail.data ?: throw Exception("Chapter not found")
        return data.urls.split(",").filter { it.isNotBlank() }.mapIndexed { index, item ->
            val file = item.substringAfterLast('/')
            Page(index, imageUrl = "https://$PRIMARY_IMAGE_HOST/kr${data.folder3}/$file")
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/lastest".toHttpUrl(), Fetch.DOCUMENT).asJsoup()
        .select(".typeSwitch button").map { it.text() }.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("검색어 입력 시 필터는 무시됩니다"),
        )
        data?.parseAs<List<String>>()?.takeIf { it.isNotEmpty() }?.let { filters.add(GenreFilter(it)) }
        filters.add(SortFilter())
        return FilterList(filters)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder {
        // The reader retries a failed image on the alternate host.
        addInterceptor { chain ->
            val request = chain.request()
            if (request.url.host != PRIMARY_IMAGE_HOST) {
                return@addInterceptor chain.proceed(request)
            }
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful) {
                    return@addInterceptor response
                }
                response.close()
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
            }
            val fallbackUrl = request.url.newBuilder().host(FALLBACK_IMAGE_HOST).build()
            chain.proceed(request.newBuilder().url(fallbackUrl).build())
        }
        return this
    }

    private suspend fun getList(page: Int, path: String, genre: Int = 0): MangasPage {
        val url = "$baseUrl$path".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("type", genre.toString())
            .addQueryParameter("pageSize", PAGE_SIZE.toString())
            .build()
        val list = client.get(url, Fetch.XHR).parseAs<ListDto>()
        return MangasPage(list.data.map { it.toSManga() }, page < list.lastPage)
    }

    private suspend fun search(page: Int, query: String): MangasPage {
        val url = "$baseUrl/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("pageSize", SEARCH_PAGE_SIZE.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        return MangasPage(result.data.list.map { it.toSManga() }, page * SEARCH_PAGE_SIZE < result.data.total)
    }

    private fun parseMangaDetails(manga: SManga, document: Document): SManga = SManga.create().apply {
        this.url = manga.url
        title = document.selectFirst(".dt-left-tt h1")!!.text()
        thumbnail_url = document.selectFirst(".dt-mn-bgimg")?.attr("style")
            ?.let { COVER_URL_REGEX.find(it)?.groupValues?.get(1) }
            ?.toAbsoluteUrl()
        author = document.selectFirst("a[href^=/author]")?.textOrNull()
        description = document.selectFirst(".detail-title2 p")?.textOrNull()
        genre = document.select("a[href^=/lastest?type=]").joinToString { it.text().removePrefix("#") }
            .ifEmpty { null }
    }

    private suspend fun fetchChapterList(manga: SManga, parentTitle: String): List<SChapter> {
        val id = manga.url.substringAfterLast('/')
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val url = "$baseUrl/api/episode/list/$id".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("order", "desc")
                .addQueryParameter("pageSize", CHAPTER_PAGE_SIZE.toString())
                .build()
            val list = client.get(url, Fetch.XHR).parseAs<EpisodeListDto>()
            chapters += list.data.map { it.toSChapter(parentTitle) }
            if (page >= list.lastPage) break
            page++
        }
        return chapters
    }

    companion object {
        private const val PAGE_SIZE = 102
        private const val SEARCH_PAGE_SIZE = 30
        private const val CHAPTER_PAGE_SIZE = 2000
        private const val PRIMARY_IMAGE_HOST = "www.pl3040.com"
        private const val FALLBACK_IMAGE_HOST = "www.pl4050.com"
        private val COVER_URL_REGEX = Regex("""url\('([^']+)'\)""")
        private val HOST_REGEX = Regex("""cookmana\d+\.com""")
    }
}
