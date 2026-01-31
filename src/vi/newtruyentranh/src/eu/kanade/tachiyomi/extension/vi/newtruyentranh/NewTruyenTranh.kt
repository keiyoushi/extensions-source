package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class NewTruyenTranh : HttpSource() {
    override val name = "NewTruyenTranh"
    override val lang = "vi"
    override val baseUrl = "https://newtruyentranh5.com"
    override val supportsLatest = true

    private val apiUrl = "https://newtruyenhot.4share.me"

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "10") // Top all

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.channels.map { it.toSManga() }
        val hasNextPage = result.loadMore?.pageInfo?.let {
            it.currentPage < it.lastPage
        } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int) = buildSearchRequest(page, "", FilterList(), "0") // Newest

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================
    private fun buildSearchRequest(page: Int, query: String, filters: FilterList, defaultSort: String): Request {
        var genreSlug: String? = null
        var sortValue: String? = null
        var statusValue: String? = null

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    if (filter.state != 0) {
                        genreSlug = filter.toUriPart()
                    }
                }
                is SortFilter -> {
                    if (filter.state != 0) {
                        sortValue = filter.toUriPart()
                    }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        statusValue = filter.toUriPart()
                    }
                }
                else -> {}
            }
        }

        if (genreSlug != null && query.isBlank()) {
            val url = "$apiUrl/page/$genreSlug".toHttpUrl().newBuilder()
                .addQueryParameter("p", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("sort", sortValue ?: defaultSort)
            if (statusValue != null) {
                addQueryParameter("status", statusValue)
            }
            addQueryParameter("p", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        buildSearchRequest(page, query, filters, "0")

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ===============================
    override fun getFilterList() = eu.kanade.tachiyomi.extension.vi.newtruyentranh.getFilterList()

    // ============================== Details ===============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/detail/").substringBefore("?")
        return GET("$apiUrl/detail/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailResponse>()
        return SManga.create().apply {
            result.channel?.let { channel ->
                title = channel.name
                thumbnail_url = channel.image.url
                description = channel.description.takeIf { it.isNotBlank() }
            }
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url.replace("/detail/", "/truyen/")
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/detail/").substringBefore("?")
        return GET("$apiUrl/detail/$id", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailResponse>()
        val chapters = mutableListOf<SChapter>()

        result.sources.forEach { source ->
            source.contents.forEach { content ->
                content.streams.forEach { stream ->
                    chapters.add(
                        SChapter.create().apply {
                            setUrlWithoutDomain(stream.remoteData.url)
                            name = stream.name
                            chapter_number = stream.index.toFloat()
                        },
                    )
                }
            }
        }

        return chapters.sortedByDescending { it.chapter_number }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringAfter("/chapter/")
        return "$baseUrl/truyen-tranh/$chapterId"
    }

    // ============================== Pages =================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url
        val url = if (chapterUrl.startsWith("http")) {
            chapterUrl
        } else {
            "$apiUrl$chapterUrl"
        }
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>()
        return result.files.mapIndexed { index, file ->
            Page(index, imageUrl = file.url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================== Utilities =============================
    private fun MangaChannel.toSManga(): SManga = SManga.create().apply {
        setUrlWithoutDomain(remoteData.url)
        title = name
        thumbnail_url = image.url
    }
}
