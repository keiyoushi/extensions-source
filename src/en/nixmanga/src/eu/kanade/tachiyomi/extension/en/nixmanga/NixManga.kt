package eu.kanade.tachiyomi.extension.en.nixmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NixManga : HttpSource() {

    override val name = "NixManga"
    override val baseUrl = "https://nixmanga.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.nixmanga.com/api/v1"

    private fun hmacSha256(message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(API_SECRET.toByteArray(), "HmacSHA256"))
        val hash = mac.doFinal(message.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getApiHeaders(endpoint: String): Headers {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val message = timestamp + "GET" + endpoint
        val signature = hmacSha256(message)

        return headersBuilder()
            .set("X-Site-ID", SITE_ID)
            .set("X-Timestamp", timestamp)
            .set("X-Signature", signature)
            .set("Accept", "*/*")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .build()
    }

    private fun apiRequest(endpoint: String, url: String): Request = GET(url, getApiHeaders(endpoint))

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "$apiUrl/comics?page=$page&per_page=24&sort=popular"
        return apiRequest(endpoint, url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PaginatedComicsDto>()
        return MangasPage(dto.comics.map { it.toSManga() }, dto.hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "$apiUrl/comics?page=$page&per_page=24&sort=latest"
        return apiRequest(endpoint, url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val endpoint = "/api/v1/comics/search"
            val url = "$apiUrl/comics/search?q=$query&page=$page"
            return apiRequest(endpoint, url)
        }

        val endpoint = "/api/v1/comics"
        val url = "$apiUrl/comics?page=$page&per_page=24".toHttpUrl().newBuilder().apply {
            val sortFilter = filters.firstInstanceOrNull<SortFilter>()
            if (sortFilter != null) {
                val sortMode = when (sortFilter.state?.index) {
                    0 -> "latest"
                    1 -> "popular"
                    2 -> "rating"
                    3 -> "name"
                    4 -> "chapters"
                    5 -> "oldest"
                    else -> "latest"
                }
                addQueryParameter("sort", sortMode)
            } else {
                addQueryParameter("sort", "latest")
            }

            filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("demographic", it) }
            filters.firstInstanceOrNull<YearFilter>()?.state?.takeIf { it.isNotEmpty() }?.let { addQueryParameter("year", it) }

            val genres = filters.firstInstanceOrNull<GenreList>()?.state
                ?.filter { it.state }
                ?.joinToString(",") { it.slug }

            if (!genres.isNullOrEmpty()) {
                addQueryParameter("genre", genres)
            }
        }.build().toString()

        return apiRequest(endpoint, url)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
        val endpoint = "/api/v1/comics/slug/$slug"
        val url = "$apiUrl/comics/slug/$slug"
        return apiRequest(endpoint, url)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<ComicDto>().toSManga()

    // ============================= Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast("#")

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url
        return chapterListRequestPaginated(slug, 1)
    }

    private fun chapterListRequestPaginated(slug: String, page: Int): Request {
        val endpoint = "/api/v1/comics/slug/$slug/chapters"
        val url = "$apiUrl/comics/slug/$slug/chapters?page=$page&per_page=100&sort=newest"
        return apiRequest(endpoint, url)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var res = response
        var dto = res.parseAs<PaginatedChaptersDto>()
        val chapters = mutableListOf<SChapter>()

        val mangaSlug = res.request.url.encodedPath
            .substringAfter("/slug/")
            .substringBefore("/")

        chapters.addAll(dto.chapters.map { it.toSChapter(mangaSlug) })

        var page = 1
        while (dto.hasNextPage) {
            page++
            val nextReq = chapterListRequestPaginated(mangaSlug, page)
            res = client.newCall(nextReq).execute()
            dto = res.parseAs<PaginatedChaptersDto>()
            chapters.addAll(dto.chapters.map { it.toSChapter(mangaSlug) })
        }

        return chapters
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("#")
        val endpoint = "/api/v1/chapters/$id"
        val url = "$apiUrl/chapters/$id?skip_view=true"
        return apiRequest(endpoint, url)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PagesDto>().toPageList()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        DemographicFilter(),
        YearFilter(),
        eu.kanade.tachiyomi.source.model.Filter.Separator(),
        GenreList(getGenreList()),
    )

    companion object {
        private const val SITE_ID = "00000000-0000-0000-0000-000000000003"
        private const val API_SECRET = "83e99999c4bfee660c375511d2738260dec1d75069b95ab89a9177e6f86f8ff5"
    }
}
