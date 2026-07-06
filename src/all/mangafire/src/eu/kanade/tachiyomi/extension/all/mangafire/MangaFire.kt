package eu.kanade.tachiyomi.extension.all.mangafire

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class MangaFire : HttpSource() {

    private val langCode: String
        get() = when (lang) {
            "es-419" -> "es-la"
            "pt-BR" -> "pt-br"
            else -> lang
        }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder()
            .addQueryParameter("order[views_30d]", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ApiResponse<MangaDto>>()
        val mangas = data.items.map { it.toSManga() }
        return MangasPage(mangas, data.meta?.hasNext ?: false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder()
            .addQueryParameter("order[chapter_updated_at]", "desc")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "50")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return Observable.defer {
            val authorQuery = filters.firstInstanceOrNull<AuthorFilter>()?.state.orEmpty()
            var authorId: String? = null

            if (authorQuery.isNotBlank()) {
                val tagReq = GET("$baseUrl/api/tags?keyword=$authorQuery", headers)
                val tagRes = client.newCall(tagReq).execute()
                val tags = tagRes.parseAs<TagResponse>()

                authorId = tags.data.firstOrNull { it.type == "author" || it.type == "artist" }?.id?.toString()

                if (authorId == null) {
                    return@defer Observable.just(MangasPage(emptyList(), false))
                }
            }

            val req = searchMangaRequest(page, query, filters, authorId)
            val res = client.newCall(req).execute()
            Observable.just(searchMangaParse(res))
        }
    }

    private fun searchMangaRequest(page: Int, query: String, filters: FilterList, authorId: String?): Request {
        val url = "$baseUrl/api/titles".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("keyword", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "50")

            if (authorId != null) {
                addQueryParameter("authors[]", authorId)
            }

            (filters.ifEmpty { getFilterList() })
                .filterIsInstance<UriFilter>()
                .forEach { it.addToUri(this) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = searchMangaRequest(page, query, filters, null)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val hid = getHid(manga.url)
        return GET("$baseUrl/api/titles/$hid", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsResponse>().data.toSManga()

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val hid = getHid(manga.url)
        var page = 1
        var lastPage: Int
        val chapters = mutableListOf<SChapter>()

        do {
            val url = "$baseUrl/api/titles/$hid/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("language", langCode)
                .addQueryParameter("sort", "number")
                .addQueryParameter("order", "desc")
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", "200")
                .build()

            val response = client.newCall(GET(url, headers)).execute()
            val data = response.parseAs<ApiResponse<ChapterDto>>()

            data.items.forEach { ch ->
                chapters.add(ch.toSChapter(manga.url, langCode))
            }

            lastPage = data.meta?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        chapters
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/").substringBefore("-")
        return GET("$baseUrl/api/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PagesResponse>()
        return data.data.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.url)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        Filter.Separator(),
        GenreModeFilter(),
        GenreFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        AuthorFilter(),
        YearFromFilter(),
        YearToFilter(),
        MinChapterFilter(),
        Filter.Separator(),
        SortFilter(),
    )

    // ============================= Utilities =============================

    private fun getHid(url: String): String {
        val lastPart = url.removeSuffix("/").substringAfterLast("/")
        return when {
            lastPart.contains(".") -> lastPart.substringAfterLast(".")
            lastPart.contains("-") -> lastPart.substringBefore("-")
            else -> lastPart
        }
    }
}
