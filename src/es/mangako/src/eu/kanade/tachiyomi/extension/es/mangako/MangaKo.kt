package eu.kanade.tachiyomi.extension.es.mangako

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaKo : KeiSource() {

    // The API allows 300 requests per minute
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 5, period = 1.seconds) { it.host == API_URL.host }

    override suspend fun getPopularManga(page: Int) = searchTitles(page, "", SORTS[0])

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.post(
            API_URL.resolve("searcher/type/search")!!,
            Fetch.XHR,
            form("inputSearch" to "", "numberPage" to page, "optionSelected" to "Más recientes", "type" to "chapters"),
        ).parseAs<ListDto<TitleRefDto>>()
        // The feed lists chapters, often several of the same title
        return MangasPage(result.datas.distinctBy { it.titleDataId }.map { it.toSManga() }, result.hasNextPage(page))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = searchTitles(page, query.trim(), filters.firstInstanceOrNull<SortFilter>()?.sort ?: SORTS[0])

    private suspend fun searchTitles(page: Int, query: String, sort: Sort): MangasPage {
        val fields = listOfNotNull(
            "inputSearch" to query,
            "numberPage" to page,
            "optionSelected" to sort.option,
            sort.type?.let { "type" to it },
        )
        val endpoint = if (sort.type == null) "searcher/search" else "searcher/type/search"
        val result = client.post(API_URL.resolve(endpoint)!!, Fetch.XHR, form(*fields.toTypedArray()))
            .parseAs<ListDto<TitleCardDto>>()
        return MangasPage(result.datas.map { it.toSManga() }, result.hasNextPage(page))
    }

    // Scan group pages and lists; title and chapter links go to getMangaByUrl
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (!url.isSite()) return MangasPage(emptyList(), false)
        val segments = url.pathSegments
        return when (segments[0]) {
            "scan" -> {
                val scanId = segments.getOrNull(1)?.toIntOrNull() ?: return MangasPage(emptyList(), false)
                val titles = client.post(API_URL.resolve("scan/public")!!, Fetch.XHR, form("scanId" to scanId))
                    .parseAs<ScanDto>().titles
                MangasPage(titles.map { it.toSManga() }, false)
            }
            "searcher" -> when (val text = segments.getOrNull(1).orEmpty()) {
                "type" -> when (segments.getOrNull(2)) {
                    "popular" -> getPopularManga(page)
                    "chapters" -> getLatestUpdates(page)
                    else -> searchTitles(page, "", SORTS[1])
                }
                else -> searchTitles(page, text, SORTS[1])
            }
            else -> super.getMangasByUrl(url, page)
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val id = url.pathSegments.getOrNull(1)?.toIntOrNull()
        if (!url.isSite() || id == null) return null
        val titleId = when (url.pathSegments[0]) {
            "title" -> id.toString()
            "chapter" -> getReader(id.toString()).titleId
            else -> return null
        }
        val manga = SManga.create().apply { this.url = "/title/$titleId" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details come with the first page of chapters, 20 per page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val titleId = manga.url.substringAfterLast('/')
        val title = client.post(API_URL.resolve("title")!!, Fetch.XHR, form("dataId" to titleId)).parseAs<TitleDto>()
        val chapterList = if (fetchChapters) {
            val firstPage = title.chapters.chapters
            val otherPages = (2..firstPage.pagination.totalPages).map { page ->
                async {
                    client.post(
                        API_URL.resolve("title/chapters")!!,
                        Fetch.XHR,
                        form("dataId" to titleId, "page" to page, "order" to "DESC"),
                    ).parseAs<TitleChaptersDto>().chapters.chapters
                }
            }.awaitAll()
            (listOf(firstPage) + otherPages).flatMap { it.volume }.flatMap { it.chapters }.map { it.toSChapter() }
        } else {
            chapters
        }
        SMangaUpdate(title.toSManga(manga.url), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = getReader(chapter.url.substringAfterLast('/'))
        if (reader.lock.locked) return emptyList()
        return reader.images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    private suspend fun getReader(chapterId: String) = client.post(API_URL.resolve("chapter")!!, Fetch.XHR, form("dataId" to chapterId)).parseAs<ReaderDto>()

    private fun form(vararg fields: Pair<String, Any>) = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .apply { fields.forEach { (name, value) -> addFormDataPart(name, value.toString()) } }
        .build()

    private fun HttpUrl.isSite() = host.removePrefix("www.") == baseUrl.toHttpUrl().host

    companion object {
        private val API_URL = "https://api.mangako.xyz/api/v1/".toHttpUrl()
    }
}
