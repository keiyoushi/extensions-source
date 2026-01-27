package eu.kanade.tachiyomi.extension.vi.newtruyentranh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
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
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "10")
            .addQueryParameter("p", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListResponse>()
        val mangas = result.channels.map { it.toSManga() }
        val hasNextPage = result.loadMore?.pageInfo?.let {
            it.currentPage < it.lastPage
        } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/page/newest".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("p", page.toString())
                .build()
            return GET(url, headers)
        }

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

        if (genreSlug != null) {
            val url = "$apiUrl/page/$genreSlug".toHttpUrl().newBuilder()
                .addQueryParameter("p", page.toString())
                .build()
            return GET(url, headers)
        }

        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            if (sortValue != null) {
                addQueryParameter("sort", sortValue)
            }
            if (statusValue != null) {
                addQueryParameter("status", statusValue)
            }
            addQueryParameter("p", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Lưu ý: Bộ lọc không dùng chung được với tìm kiếm"),
        Filter.Separator(),
        GenreFilter(),
        SortFilter(),
        StatusFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Thể loại",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Âu Cổ", "au-co"),
            Pair("Chuyển Sinh", "chuyen-sinh"),
            Pair("Cổ Đại", "co-dai"),
            Pair("Comedy", "comedy"),
            Pair("Comic", "comic"),
            Pair("Detective", "detective"),
            Pair("Đô Thị", "do-thi"),
            Pair("Đời Thường", "doi-thuong"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Hài Hước", "hai-huoc"),
            Pair("Hành động", "hanh-dong"),
            Pair("Harem", "harem"),
            Pair("Hậu Cung", "hau-cung"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Hiện đại", "hien-dai"),
            Pair("Historical", "historical"),
            Pair("Học đường", "hoc-duong"),
            Pair("Horror", "horror"),
            Pair("Huyền Huyễn", "huyen-huyen"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Kinh Dị", "kinh-di"),
            Pair("Magic", "magic"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mạt Thế", "mat-the"),
            Pair("Mature", "mature"),
            Pair("Mystery", "mystery"),
            Pair("Ngôn Tình", "ngon-tinh"),
            Pair("One shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Trọng Sinh", "trong-sinh"),
            Pair("Trùng Sinh", "trung-sinh"),
            Pair("Truyện Chill", "truyen-chill"),
            Pair("Truyện Đánh đấm", "truyen-danh-dam"),
            Pair("Truyện Học viện", "truyen-hoc-vien"),
            Pair("Truyện Manga", "truyen-manga"),
            Pair("Truyện Manga màu", "truyen-manga-mau"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Truyện Sci-fi", "truyen-sci-fi"),
            Pair("Truyện Siêu năng lực", "truyen-sieu-nang-luc"),
            Pair("Truyện Thể thao", "truyen-the-thao"),
            Pair("Truyện Võ lâm", "truyen-vo-lam"),
            Pair("Truyện Xã hội đen", "truyen-xa-hoi-den"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Webtoon", "webtoon"),
            Pair("Xuyên Không", "xuyen-khong"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

    private class SortFilter : UriPartFilter(
        "Xếp hạng",
        arrayOf(
            Pair("Mặc định", ""),
            Pair("Top all", "10"),
            Pair("Top tháng", "11"),
            Pair("Top tuần", "12"),
            Pair("Top ngày", "13"),
            Pair("Truyện mới", "15"),
            Pair("Số chương", "30"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Trạng thái",
        arrayOf(
            Pair("Tất cả", ""),
            Pair("Đang tiến hành", "1"),
            Pair("Đã hoàn thành", "2"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // ============================== Details ===============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfter("/detail/").substringBefore("?")
        return GET("$apiUrl/detail/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
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
        val result = response.parseAs<ChapterListResponse>()
        val chapters = mutableListOf<SChapter>()

        result.sources.forEach { source ->
            source.contents.forEach { content ->
                content.streams.forEach { stream ->
                    chapters.add(
                        SChapter.create().apply {
                            url = stream.remoteData.url
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
        url = remoteData.url.replace(apiUrl, "")
        title = name
        thumbnail_url = image.url
    }
}
