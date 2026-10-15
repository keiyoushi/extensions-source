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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
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
        rateLimit(3)
        addInterceptor(ScrambleInterceptor())
        addInterceptor(ajaxInterceptor())
    }

    private val preferences by getPreferencesLazy()

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

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "-views")
            .addQueryParameter("page", page.toString())
            .build()
        return getMangaList(url)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return getMangaList(url)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: "-updated_at"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val minRating = filters.firstInstanceOrNull<MinRatingFilter>()?.toUriPart().orEmpty()
        val isAiTranslated = filters.firstInstanceOrNull<AiTranslationFilter>()?.toUriPart().orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()

        val acceptGenres = genres
            .filter { it.state == Filter.TriState.STATE_INCLUDE }
            .joinToString(",") { it.id.toString() }
        val rejectGenres = genres
            .filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .joinToString(",") { it.id.toString() }

        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (sort.isNotEmpty()) {
                    addQueryParameter("sort", sort)
                }
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (status.isNotEmpty()) {
                    addQueryParameter("filter[status]", status)
                }
                if (minRating.isNotEmpty()) {
                    addQueryParameter("filter[min_rating]", minRating)
                }
                if (isAiTranslated.isNotEmpty()) {
                    addQueryParameter("filter[is_ai_translated]", isAiTranslated)
                }
                if (acceptGenres.isNotEmpty()) {
                    addQueryParameter("filter[accept_genres]", acceptGenres)
                }
                if (rejectGenres.isNotEmpty()) {
                    addQueryParameter("filter[reject_genres]", rejectGenres)
                }
            }
            .build()

        return getMangaList(url)
    }

    private suspend fun getMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url.toString()).asJsoup()

        val mangaCards = document.select(".manga-vertical")
        val mangas = mangaCards.mapNotNull { card ->
            val link = card.selectFirst("h3 a") ?: card.selectFirst("a[href*=/truyen/]") ?: return@mapNotNull null
            val href = link.attr("href").ifEmpty { return@mapNotNull null }
            val slug = href.toHttpUrl().encodedPath

            SManga.create().apply {
                this.url = slug
                title = link.text().trim()
                thumbnail_url = card.selectFirst(".cover-frame img, img")?.absUrl("src")?.ifEmpty { null }
            }
        }

        val hasNextPage = document.selectFirst("nav[aria-label=Pagination] a[aria-label=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================

    private fun isLoginRequired(text: String): Boolean = text.contains("\"code\":\"login_required\"") ||
        text.contains("Login required", ignoreCase = true) ||
        text.contains("Yêu cầu đăng nhập", ignoreCase = true)

    private suspend fun fetchHtmlDocument(url: String): Document {
        val response = client.get(url, ensureSuccess = false)
        if (response.code == 403) {
            response.close()
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        val document = response.asJsoup()
        if (isLoginRequired(document.text())) {
            throw Exception("Truyện này cần đăng nhập webview bằng tài khoản phù hợp để xem")
        }
        return document
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null

        val document = fetchHtmlDocument("$baseUrl/truyen/$slug")
        return parseDetailsFromHtml(document, slug)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.trimStart('/').substringAfterLast('/')
        val document = fetchHtmlDocument("$baseUrl/truyen/$slug")

        return SMangaUpdate(
            manga = parseDetailsFromHtml(document, slug).apply { this.url = manga.url },
            chapters = parseChaptersFromHtml(document),
        )
    }

    private fun parseDetailsFromHtml(document: Document, slug: String): SManga = SManga.create().apply {
        url = "/truyen/$slug"
        title = document.selectFirst("h1.md-title, h1")!!.text()
        thumbnail_url = document.selectFirst(".md-cover img, .cover-frame img")?.absUrl("src")?.ifEmpty { null }
            ?: document.selectFirst("meta[property=og:image]")?.attrOrNull("content")?.ifEmpty { null }
        description = document.selectFirst(".md-synopsis p[x-ref=text], .md-synopsis p")?.text()?.trim()?.ifEmpty { null }
        author = document.select(".md-rail dt:contains(Tác giả) + dd a, .md-rail dt:contains(Tác giả) + dd span")
            .joinToString { it.text().trim() }.ifEmpty { null }
        artist = document.select(".md-rail dt:contains(Họa sĩ) + dd a, .md-rail dt:contains(Họa sĩ) + dd span")
            .joinToString { it.text().trim() }.ifEmpty { null }
        genre = document.select(".md-rail-genres a.md-chip, .md-rail dt:contains(Thể loại) + dd a.md-chip")
            .joinToString { it.text().trim() }.ifEmpty { null }
        status = when {
            document.selectFirst(".md-badge-done") != null -> SManga.COMPLETED
            document.selectFirst(".md-badge")?.text()?.contains("hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
            document.selectFirst(".md-badge-ongoing") != null -> SManga.ONGOING
            document.selectFirst(".md-badge")?.text()?.contains("đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChaptersFromHtml(document: Document): List<SChapter> {
        val chapterLinks = document.select("ul#md-chapter-list li a.md-ch, a.md-ch")
        return chapterLinks.map { a ->
            SChapter.create().apply {
                url = a.absUrl("href").toHttpUrl().encodedPath
                name = a.selectFirst(".md-ch-title")?.text()?.trim() ?: a.text().trim()
                date_upload = parseRelativeDate(a.selectFirst(".md-ch-meta span")?.text())
            }
        }
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
        val response = client.get("$baseUrl/truyen/$slug", ensureSuccess = false)
        if (response.code == 403) {
            response.close()
            return emptyList()
        }
        val document = response.asJsoup()
        if (isLoginRequired(document.text())) return emptyList()

        return document.select("section[aria-labelledby=md-related-heading] ul.md-rel li a, ul.md-rel li a").mapNotNull { a ->
            val href = a.attr("href").ifEmpty { return@mapNotNull null }
            val mangaUrl = href.toHttpUrl().encodedPath
            val title = a.selectFirst(".md-rel-name")?.text()?.trim() ?: a.text().trim()
            if (title.isEmpty()) return@mapNotNull null
            SManga.create().apply {
                url = mangaUrl
                this.title = title
                thumbnail_url = a.selectFirst(".md-thumb img, img")?.absUrl("src")?.ifEmpty { null }
            }
        }
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
        return text
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/tim-kiem").asJsoup()
        val genreRegex = Regex("""toggleGenre\('([^']+)'\)""")

        val genres = document.select("button").mapNotNull { button ->
            val click = button.attr("@click").ifEmpty { button.attr("x-on:click") }
            val match = genreRegex.find(click) ?: return@mapNotNull null
            val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val name = button.text().trim().ifEmpty { return@mapNotNull null }
            GenreOption(id, name)
        }.distinctBy { it.id }

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())
}
