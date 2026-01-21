package eu.kanade.tachiyomi.extension.all.weebdex

import eu.kanade.tachiyomi.extension.all.weebdex.dto.ChapterDto
import eu.kanade.tachiyomi.extension.all.weebdex.dto.ChapterListDto
import eu.kanade.tachiyomi.extension.all.weebdex.dto.MangaDto
import eu.kanade.tachiyomi.extension.all.weebdex.dto.MangaListDto
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

open class WeebDex(
    override val lang: String,
    private val weebdexLang: String = lang,
) : HttpSource() {
    override val name = "WeebDex"
    override val baseUrl = "https://weebdex.org"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(WeebDexConstants.RATE_LIMIT)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // -------------------- Popular --------------------

    override fun popularMangaRequest(page: Int): Request {
        val url = WeebDexConstants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "views")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .apply {
                if (weebdexLang != "all") {
                    addQueryParameter("availableTranslatedLang", weebdexLang)
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaListDto = response.parseAs<MangaListDto>()
        val mangas = mangaListDto.toSMangaList()
        return MangasPage(mangas, mangaListDto.hasNextPage)
    }

    // -------------------- Latest --------------------
    override fun latestUpdatesRequest(page: Int): Request {
        val url = WeebDexConstants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "updatedAt")
            .addQueryParameter("order", "desc")
            .addQueryParameter("hasChapters", "1")
            .apply {
                if (weebdexLang != "all") {
                    addQueryParameter("availableTranslatedLang", weebdexLang)
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Search --------------------
    override fun getFilterList(): FilterList = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = WeebDexConstants.API_MANGA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (weebdexLang != "all") {
                    addQueryParameter("availableTranslatedLang", weebdexLang)
                }
            }

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query)
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is TagList -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                WeebDexConstants.tags[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tag", tagId)
                                }
                            }
                        }
                    }
                    is TagsExcludeFilter -> {
                        filter.state.forEach { tag ->
                            if (tag.state) {
                                WeebDexConstants.tags[tag.name]?.let { tagId ->
                                    urlBuilder.addQueryParameter("tagx", tagId)
                                }
                            }
                        }
                    }
                    is TagModeFilter -> urlBuilder.addQueryParameter("tmod", filter.state.toString())
                    is TagExcludeModeFilter -> urlBuilder.addQueryParameter("txmod", filter.state.toString())
                    else -> { /* Do Nothing */ }
                }
            }
        }

        // Separated explicitly to be applied even when a search query is applied.
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> urlBuilder.addQueryParameter("sort", filter.selected)
                is OrderFilter -> urlBuilder.addQueryParameter("order", filter.selected)
                is StatusFilter -> filter.selected?.let { urlBuilder.addQueryParameter("status", it) }
                is DemographicFilter -> filter.selected?.let { urlBuilder.addQueryParameter("demographic", it) }
                is ContentRatingFilter -> filter.selected?.let { urlBuilder.addQueryParameter("contentRating", it) }
                is LangFilter -> filter.query?.let { urlBuilder.addQueryParameter("lang", it) }
                is HasChaptersFilter -> if (filter.state) urlBuilder.addQueryParameter("hasChapters", "1")
                is YearFromFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearFrom", it) }
                is YearToFilter -> filter.state.takeIf { it.isNotEmpty() }?.let { urlBuilder.addQueryParameter("yearTo", it) }
                else -> { /* Do Nothing */ }
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // -------------------- Manga details --------------------

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegments(manga.url)
            .removePathSegment(0)
            .setPathSegment(0, "title")
            .build().toString()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("${WeebDexConstants.API_URL}${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = response.parseAs<MangaDto>()
        return manga.toSManga()
    }

    // -------------------- Chapters --------------------

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        // chapter list is paginated; get all pages
        val url = "${WeebDexConstants.API_URL}${manga.url}/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("order", "desc")
            .apply {
                if (weebdexLang != "all") {
                    addQueryParameter("tlang", weebdexLang)
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // Recursively parse pages
        fun parsePage(chapterListDto: ChapterListDto) {
            chapters.addAll(chapterListDto.toSChapterList())
            if (chapterListDto.hasNextPage) {
                val nextUrl = response.request.url.newBuilder()
                    .setQueryParameter("page", (chapterListDto.page + 1).toString())
                    .build()
                val nextResponse = client.newCall(GET(nextUrl, headers)).execute()
                val nextChapterListDto = nextResponse.parseAs<ChapterListDto>()
                parsePage(nextChapterListDto)
            }
        }

        parsePage(response.parseAs<ChapterListDto>())
        return chapters
    }

    // -------------------- Pages --------------------

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("${WeebDexConstants.API_URL}${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = response.parseAs<ChapterDto>()
        return chapter.toPageList()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")
}
