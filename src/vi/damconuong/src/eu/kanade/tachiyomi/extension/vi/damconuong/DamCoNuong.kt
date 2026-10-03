package eu.kanade.tachiyomi.extension.vi.damconuong

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class DamCoNuong : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
        addInterceptor(ScrambleInterceptor())
        addInterceptor(ajaxInterceptor())
    }

    private val preferences by getPreferencesLazy()

    // API and Auth API complete hidden in backend can't scraping
    private val api = "https://api.damconuong.pw/api/v1"

    private fun ajaxInterceptor() = Interceptor { chain ->
        val request = chain.request()
        if (request.url.encodedPath.startsWith("/_c/")) {
            val newRequest = request.newBuilder()
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json")
                .build()
            return@Interceptor chain.proceed(newRequest)
        }
        chain.proceed(request)
    }

    private fun isLoginRequired(text: String): Boolean = text.contains("\"code\":\"login_required\"") ||
        text.contains("Login required", ignoreCase = true) ||
        text.contains("Yêu cầu đăng nhập", ignoreCase = true)

    private suspend fun fetchJson(url: String): String {
        val text = client.get(url, ensureSuccess = false).use { it.body.string() }
        if (isLoginRequired(text)) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        return text
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchMangaList(page, query = "", filters = FilterList(SortFilter().apply { state = 3 }))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchMangaList(page, query = "", filters = FilterList(SortFilter().apply { state = 0 }))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchMangaList(page, query, filters)

    private suspend fun fetchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "-updated_at"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val minRating = filters.firstInstanceOrNull<MinRatingFilter>()?.toUriPart().orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()

        val acceptGenres = genres
            .filter { it.state == Filter.TriState.STATE_INCLUDE }
            .joinToString(",") { it.id.toString() }
        val rejectGenres = genres
            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .joinToString(",") { it.id.toString() }

        val url = "$api/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "24")
            .addQueryParameter("sort", sort)
            .addQueryParameter("include", "genres,artist,latest_chapter")
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (status.isNotEmpty()) {
                    addQueryParameter("filter[status]", status)
                }
                if (acceptGenres.isNotEmpty()) {
                    addQueryParameter("filter[accept_genres]", acceptGenres)
                }
                if (rejectGenres.isNotEmpty()) {
                    addQueryParameter("filter[reject_genres]", rejectGenres)
                }
                if (minRating.isNotEmpty()) {
                    addQueryParameter("filter[min_rating]", minRating)
                }
            }
            .build()

        return client.get(url).parseAs<ListResponse>().toMangasPage()
    }

    private fun ListResponse.toMangasPage(): MangasPage {
        val pagination = meta?.pagination
        val hasNextPage = pagination != null && pagination.currentPage < pagination.lastPage
        return MangasPage(data.map { it.toSManga() }, hasNextPage)
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return fetchMangaDetails(slug)
    }

    // Some manga need login, api doesn't support auth, need html scraping
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trimStart('/').substringAfterLast('/')

        if (manga.memo["needs_login"]?.booleanOrNull == true) {
            val doc = fetchHtmlDocument(slug)
            return SMangaUpdate(
                manga = parseDetailsFromHtml(doc, slug).apply { this.url = manga.url },
                chapters = parseChaptersFromHtml(doc),
            )
        }

        if (!fetchDetails) {
            if (!fetchChapters) return SMangaUpdate(manga, chapters)

            val apiUrl = "$api/mangas/$slug/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("page", "1")
                .addQueryParameter("per_page", "2000")
                .addQueryParameter("sort", "desc")
                .build()
                .toString()

            val firstText = client.get(apiUrl, ensureSuccess = false).use { it.body.string() }
            if (isLoginRequired(firstText)) {
                val doc = fetchHtmlDocument(slug)
                return SMangaUpdate(
                    manga = parseDetailsFromHtml(doc, slug).apply { this.url = manga.url },
                    chapters = parseChaptersFromHtml(doc),
                )
            }

            return SMangaUpdate(
                manga = manga,
                chapters = fetchChapterListFromApi(slug, firstText),
            )
        }

        val apiUrl = "$api/mangas/$slug?include=artist,author,group,genres"
        val responseText = client.get(apiUrl, ensureSuccess = false).use { it.body.string() }
        if (isLoginRequired(responseText)) {
            val doc = fetchHtmlDocument(slug)
            return SMangaUpdate(
                manga = parseDetailsFromHtml(doc, slug).apply { this.url = manga.url },
                chapters = parseChaptersFromHtml(doc),
            )
        }

        val dto = responseText.parseAs<DetailResponse>().data
        val details = dto.toSMangaDetails().apply {
            this.url = manga.url
            memo = buildJsonObject {
                dto.group?.slug?.let { put("group_slug", it) }
                dto.author?.slug?.let { put("author_slug", it) }
                dto.artist?.slug?.let { put("artist_slug", it) }
                dto.genres.firstOrNull()?.slug?.let { put("genre_slug", it) }
            }
        }

        val chapterList = if (fetchChapters) {
            fetchChapterListFromApi(slug)
        } else {
            chapters
        }

        return SMangaUpdate(
            manga = details,
            chapters = chapterList,
        )
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val apiUrl = "$api/mangas/$slug?include=artist,author,group,genres"
        val responseText = client.get(apiUrl, ensureSuccess = false).use { it.body.string() }
        if (!isLoginRequired(responseText)) {
            val dto = responseText.parseAs<DetailResponse>().data
            return dto.toSMangaDetails().apply {
                memo = buildJsonObject {
                    dto.group?.slug?.let { put("group_slug", it) }
                    dto.author?.slug?.let { put("author_slug", it) }
                    dto.artist?.slug?.let { put("artist_slug", it) }
                    dto.genres.firstOrNull()?.slug?.let { put("genre_slug", it) }
                }
            }
        }
        val doc = fetchHtmlDocument(slug)
        return parseDetailsFromHtml(doc, slug)
    }

    private suspend fun fetchHtmlDocument(slug: String): Document {
        val url = "$baseUrl/truyen/$slug"
        val response = client.get(url, ensureSuccess = false)
        val text = response.use { it.body.string() }
        if (response.code == 403 || isLoginRequired(text)) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        val document = Jsoup.parse(text, url)
        if (isLoginRequired(document.text())) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        return document
    }

    private fun parseDetailsFromHtml(document: Document, slug: String): SManga = SManga.create().apply {
        this.url = "/truyen/$slug"
        title = document.selectFirst("h1.md-title, h1")!!.text().trim()
        thumbnail_url = document.selectFirst(".md-cover img")?.absUrl("src")?.ifEmpty { null }
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null }
        val synopsisEl = document.selectFirst(".md-synopsis")
        synopsisEl?.select("button, dialog")?.remove()
        description = synopsisEl?.text()?.trim()?.ifEmpty { null }
        author = document.select(".md-rail dt:contains(Tác giả) + dd a, .md-rail dt:contains(Tác giả) + dd span")
            .joinToString { it.text().trim() }.ifEmpty { null }
        artist = document.select(".md-rail dt:contains(Họa sĩ) + dd a, .md-rail dt:contains(Họa sĩ) + dd span")
            .joinToString { it.text().trim() }.ifEmpty { null }
        genre = document.select(".md-rail-genres a.md-chip")
            .joinToString { it.text().trim() }.ifEmpty { null }
        status = when {
            document.selectFirst(".md-badge-done") != null -> SManga.COMPLETED
            document.selectFirst(".md-badge")?.text()?.contains("hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
            else -> SManga.ONGOING
        }
        memo = buildJsonObject {
            put("needs_login", true)
        }
    }

    private fun parseChaptersFromHtml(document: Document): List<SChapter> {
        val chapterLinks = document.select("a.md-ch")
        if (chapterLinks.isEmpty() && isLoginRequired(document.text())) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        return chapterLinks.map { a ->
            SChapter.create().apply {
                this.url = a.absUrl("href").toHttpUrl().encodedPath
                name = a.selectFirst(".md-ch-title")?.text()?.trim() ?: a.text().trim()
                date_upload = parseRelativeDate(a.selectFirst(".md-ch-meta span")?.text())
            }
        }
    }

    private suspend fun fetchChapterListFromApi(mangaSlug: String, initialJson: String? = null): List<SChapter> {
        val apiUrl = "$api/mangas/$mangaSlug/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", "1")
            .addQueryParameter("per_page", "2000")
            .addQueryParameter("sort", "desc")
            .build()
            .toString()

        var text = initialJson ?: fetchJson(apiUrl)
        val result = mutableListOf<SChapter>()
        var page = 1
        var lastPage = 1

        do {
            if (page > 1) {
                text = fetchJson(
                    "$api/mangas/$mangaSlug/chapters".toHttpUrl().newBuilder()
                        .addQueryParameter("page", page.toString())
                        .addQueryParameter("per_page", "2000")
                        .addQueryParameter("sort", "desc")
                        .build()
                        .toString(),
                )
            }
            val response = text.parseAs<ChapterListResponse>()
            result += response.data.map { it.toSChapter(mangaSlug) }
            lastPage = response.meta?.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        return result
    }

    private val numberRegex = Regex("""\d+""")
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val siteZone = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        when {
            dateStr.contains("Vừa xong", ignoreCase = true) || dateStr.contains("vừa đăng", ignoreCase = true) ->
                return Clock.System.now().toEpochMilliseconds()
            dateStr.contains("Hôm nay", ignoreCase = true) ->
                return Clock.System.now().toEpochMilliseconds()
            dateStr.contains("Hôm qua", ignoreCase = true) ->
                return (Clock.System.now() - 1.days).toEpochMilliseconds()
        }

        val number = numberRegex.find(dateStr)?.value?.toIntOrNull()
            ?: return dateFormat.tryParseDate(dateStr, siteZone)

        val duration = when {
            dateStr.contains("giây") -> number.seconds
            dateStr.contains("phút") -> number.minutes
            dateStr.contains("giờ") -> number.hours
            dateStr.contains("ngày") -> number.days
            dateStr.contains("tuần") -> (number * 7).days
            dateStr.contains("tháng") -> (number * 30).days
            dateStr.contains("năm") -> (number * 365).days
            else -> return dateFormat.tryParseDate(dateStr, siteZone)
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val slug = manga.url.trimStart('/').substringAfterLast('/')
        val sources = listOfNotNull(
            manga.memo["group_slug"]?.stringOrNull?.let { "groups" to it },
            manga.memo["author_slug"]?.stringOrNull?.let { "authors" to it },
            manga.memo["artist_slug"]?.stringOrNull?.let { "artists" to it },
            manga.memo["genre_slug"]?.stringOrNull?.let { "genres" to it },
        ).ifEmpty {
            if (manga.memo["needs_login"]?.booleanOrNull == true) {
                return emptyList()
            }
            val detail = fetchJson("$api/mangas/$slug?include=artist,author,group,genres")
                .parseAs<DetailResponse>()
                .data
            listOfNotNull(
                detail.group?.slug?.let { "groups" to it },
                detail.author?.slug?.let { "authors" to it },
                detail.artist?.slug?.let { "artists" to it },
                detail.genres.firstOrNull()?.slug?.let { "genres" to it },
            )
        }

        for ((type, taxonomySlug) in sources) {
            val list = client.get("$api/$type/$taxonomySlug/mangas?per_page=12")
                .parseAs<ListResponse>()
                .data
            val related = list.filter { it.slug != slug }.map { it.toSManga() }
            if (related.isNotEmpty()) return related.take(12)
        }
        return emptyList()
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trimStart('/').split('/')
        val mangaSlug = segments.getOrNull(1)
        val chapterSlug = segments.getOrNull(2)
        if (mangaSlug.isNullOrEmpty() || chapterSlug.isNullOrEmpty()) {
            throw Exception("Invalid chapter url: ${chapter.url}")
        }

        val path = "$mangaSlug/$chapterSlug"
        PagesCrypto.ensureLoaded(client, baseUrl, preferences)
        val token = PagesCrypto.token(mangaSlug, chapterSlug)
        val response = fetchPagesJson(mangaSlug, chapterSlug, token)
            .parseAs<PagesResponse>()

        val payload = PagesCrypto.decryptPages(response.encrypted, token, path)
        return payload.pages.mapIndexedNotNull { index, src ->
            if (src.isBlank()) return@mapIndexedNotNull null
            val key = payload.scrambleKeys?.getOrNull(index)?.takeIf { it.isNotEmpty() }
            val imageUrl = if (key != null) "$src#$key" else src
            Page(index, url = imageUrl, imageUrl = imageUrl)
        }
    }

    private suspend fun fetchPagesJson(mangaSlug: String, chapterSlug: String, token: String): String {
        val cUrl = "$baseUrl/_c/mangas/$mangaSlug/chapters/$chapterSlug/pages?_=$token"
        val text = client.get(cUrl, ensureSuccess = false).use { it.body.string() }
        if (text.contains("\"e\":")) {
            return text
        }
        if (isLoginRequired(text)) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        val fallbackUrl = "$api/mangas/$mangaSlug/chapters/$chapterSlug/pages?_=$token"
        val fallbackText = client.get(fallbackUrl, ensureSuccess = false).use { it.body.string() }
        if (isLoginRequired(fallbackText)) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        return fallbackText
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = mutableListOf<GenreOption>()
        var page = 1
        var lastPage = 1

        do {
            val response = client.get("$api/genres?per_page=100&page=$page")
                .parseAs<GenreListResponse>()
            genres += response.data
            lastPage = response.meta?.pagination?.lastPage ?: 1
            page++
        } while (page <= lastPage)

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())
}
