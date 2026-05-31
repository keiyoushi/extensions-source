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

class NixManga : HttpSource() {

    override val name = "NixManga"
    override val baseUrl = "https://nixmanga.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "https://api.nixmanga.com"

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val req = chain.request()

            if (req.url.host == apiUrl.toHttpUrl().host) {
                var response = chain.proceed(req)
                if (response.code == 401) {
                    response.close()

                    val newReq = req.newBuilder().apply {
                        headers(getApiHeaders(req.url.encodedPath, refresh = true))
                    }.build()
                    response = chain.proceed(newReq)
                }
                return@addInterceptor response
            }
            chain.proceed(req)
        }
        .build()

    private var cachedSlot: String = ""
    private var cachedToken: String = ""
    private var cachedSignature: String = ""

    private val signerJsUrl = "$apiUrl/_nix/signer.js"
    private val signerJsRegex = Regex("const z=\\[(.*?)\\],")

    private fun refreshAuthValues(endpoint: String) {
        val response = client.newCall(GET(signerJsUrl, headers)).execute()
        val body = response.body?.string() ?: error("Failed to fetch signer.js")
        response.close()

        val match = signerJsRegex.find(body)
        val zArr = match!!.groupValues[1].split(",").map { it.trim().removeSurrounding("\"") }

        fun reverse(s: String) = s.reversed()
        fun rJoin(arr: List<String>) = arr.joinToString("") { reverse(it) }

        val slot = reverse(zArr[0])
        val token = rJoin(zArr.slice(4 until zArr.size))
        val k = rJoin(zArr.slice(1..3))

        val payload = "GET|$endpoint|$SITE_ID|$slot|$token|$k"
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        val sig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash)

        cachedSlot = slot
        cachedToken = token
        cachedSignature = sig
    }

    private fun getApiHeaders(endpoint: String, refresh: Boolean = false): Headers {
        if (refresh || cachedSlot.isEmpty()) refreshAuthValues(endpoint)

        return headersBuilder()
            .set("x-web-token", cachedToken)
            .set("x-web-signature", cachedSignature)
            .set("x-web-slot", cachedSlot)
            .set("x-site-id", SITE_ID)
            .set("Accept", "*/*")
            .set("Origin", baseUrl)
            .set("Referer", "$baseUrl/")
            .set("sec-fetch-site", "same-site")
            .build()
    }

    private fun apiRequest(endpoint: String, path: String): Request = GET("$apiUrl/api/v1$path", getApiHeaders(endpoint))

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24&sort=popular"
        return apiRequest(endpoint, url)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<PaginatedComicsDto>()
        return MangasPage(dto.comics.map { it.toSManga() }, dto.hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24&sort=latest"
        return apiRequest(endpoint, url)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val endpoint = "/api/v1/comics/search"
            val url = "/comics/search?q=$query&page=$page"
            return apiRequest(endpoint, url)
        }

        val endpoint = "/api/v1/comics"
        val url = "/comics?page=$page&per_page=24".toHttpUrl().newBuilder().apply {
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
        val url = "/comics/slug/$slug"
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
        val url = "/comics/slug/$slug/chapters?page=$page&per_page=100&sort=newest"
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
        val url = "/chapters/$id?skip_view=true"
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
