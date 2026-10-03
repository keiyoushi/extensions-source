package eu.kanade.tachiyomi.extension.pt.egotoons

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

@Source
abstract class EgoToons : KeiSource() {

    private val apiUrl = "https://api.egotoons.com"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, defaultSort = "populares")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, defaultSort = "recentes")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query, filters, defaultSort = "recentes")

    private suspend fun getMangaList(
        page: Int,
        query: String = "",
        filters: FilterList = FilterList(),
        defaultSort: String,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: defaultSort
        val url = "$baseUrl/api/obras.js".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", MANGA_PAGE_SIZE.toString())
            .addQueryParameter("ordenarPor", sort)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("busca", query)
                }
                filters.filterIsInstance<UrlFilter>()
                    .filterNot { it is SortFilter }
                    .forEach { it.addToUrl(this) }
            }
            .build()

        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.mangas.map { it.toSManga(baseUrl) }, result.pagination.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.") || url.pathSegments.firstOrNull() != "obra") return null
        val mangaId = url.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/obra/$mangaId" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = manga.url.mangaId()
        val details = if (fetchDetails) {
            async {
                client.get("$baseUrl/api/obras/$mangaId")
                    .parseAs<MangaDetailsDto>()
                    .manga
                    .toSManga(baseUrl)
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) async { getChapterList(mangaId) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun getChapterList(mangaId: Int): List<SChapter> = coroutineScope {
        val firstPage = getChapterPage(mangaId, 1)
        val remaining = (2..firstPage.pagination.totalPages).map { page ->
            async { getChapterPage(mangaId, page).chapters }
        }.awaitAll().flatten()

        (firstPage.chapters + remaining).map { it.toSChapter() }
    }

    private suspend fun getChapterPage(mangaId: Int, page: Int): ChapterListDto {
        val url = "$baseUrl/api/obras/$mangaId/capitulos.js".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", CHAPTER_PAGE_SIZE.toString())
            .addQueryParameter("ordenar", "desc")
            .addQueryParameter("fields", "list")
            .build()
        return client.get(url).parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.url.mangaId()
        val chapterNumber = chapter.url.chapterNumber()
        val chapterId = client.get("$baseUrl/api/obras/$mangaId/capitulos/$chapterNumber/index.js")
            .parseAs<ChapterDetailsDto>()
            .chapter
            .id

        // The reader manifest and CDN pages are only served from the API host and require
        // the chapter's reader page as Referer.
        val readerUrl = "$baseUrl/obra/$mangaId/capitulo/$chapterNumber"
        val headers = headersBuilder()
            .set("Referer", readerUrl)
            .build()
        val cdnUrl = apiUrl.toHttpUrl()

        val pages = mutableListOf<Page>()
        var offset = 0
        while (true) {
            val manifest = client.get(
                "$apiUrl/api/leitor/capitulos/$chapterId/manifest?offset=$offset&limit=$MANIFEST_PAGE_SIZE",
                headers,
            ).parseAs<ChapterManifestDto>()
            manifest.pages.mapTo(pages) {
                Page(it.index, readerUrl, imageUrl = requireNotNull(cdnUrl.resolve(it.url)).toString())
            }
            offset = manifest.nextOffset ?: break
        }
        return pages
    }

    override fun imageRequest(page: Page): Request = Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headersBuilder()
                .set("Referer", page.url.ifEmpty { "$baseUrl/" })
                .build(),
        )
        .get()
        .build()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val formats = async {
            client.get("$baseUrl/api/filtros/formatos.js").parseAs<FormatListDto>().formats
        }
        val statuses = async {
            client.get("$baseUrl/api/filtros/status.js").parseAs<StatusListDto>().statuses
        }
        val tags = async {
            client.get("$baseUrl/api/filtros/tags.js").parseAs<TagListDto>().tags
        }

        FilterData(formats.await(), statuses.await(), tags.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList(SortFilter())
        return FilterList(
            SortFilter(),
            FormatFilter(filterData.formats),
            StatusFilter(filterData.statuses),
            TagFilter(filterData.tags),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/obra/${manga.url.mangaId()}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/obra/${chapter.url.mangaId()}/capitulo/${chapter.url.chapterNumber()}"

    private fun String.mangaId(): Int {
        val path = normalizedPath()
        val index = path.indexOfFirst { it == "obra" || it == "manga" }
        return path.getOrNull(index + 1)?.toIntOrNull() ?: throw Exception("Invalid manga URL")
    }

    private fun String.chapterNumber(): String {
        val path = normalizedPath().filter(String::isNotBlank)
        val index = path.indexOfFirst { it == "capitulo" || it == "chapter" }
        return path.getOrNull(index + 1) ?: throw Exception("Invalid chapter URL")
    }

    private fun String.normalizedPath(): List<String> = toHttpUrlOrNull()?.pathSegments ?: "$baseUrl/${trimStart('/')}".toHttpUrl().pathSegments

    companion object {
        private const val MANGA_PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 80
        private const val MANIFEST_PAGE_SIZE = 24
    }
}
