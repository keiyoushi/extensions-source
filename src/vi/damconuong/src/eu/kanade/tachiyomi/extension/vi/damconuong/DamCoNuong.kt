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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

@Source
abstract class DamCoNuong : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(5)
        addInterceptor(ScrambleInterceptor())
    }

    private val preferences by getPreferencesLazy()

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

        val url = "$baseUrl/danh-sach".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", sort)
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

        return client.get(url).asJsoup().toMangasPage()
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select("div.manga-vertical").mapNotNull { card ->
            val link = card.selectFirst("h3 a[href]") ?: return@mapNotNull null
            val path = link.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
            SManga.create().apply {
                url = path
                title = link.text().trim()
                thumbnail_url = card.selectFirst("img.cover")?.absUrl("src")?.ifBlank { null }
            }
        }
        return MangasPage(mangas, hasNextPage = selectFirst("a[aria-label=Next]") != null)
    }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (segments.firstOrNull() != "truyen") return null
        val slug = segments.getOrNull(1) ?: return null
        return fetchDetails("/truyen/$slug")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get("$baseUrl${manga.url}").asJsoup()
        doc.requireAccess()

        return SMangaUpdate(
            manga = if (fetchDetails) doc.toSMangaDetails().apply { url = manga.url } else manga,
            chapters = if (fetchChapters) doc.toChapterList() else chapters,
        )
    }

    private suspend fun fetchDetails(path: String): SManga {
        val doc = client.get("$baseUrl$path").asJsoup()
        doc.requireAccess()
        return doc.toSMangaDetails().apply { url = path }
    }

    private fun Document.requireAccess() {
        if (selectFirst("h1.md-title") == null && text().contains("Yêu cầu đăng nhập")) {
            throw Exception(LOGIN_REQUIRED_MESSAGE)
        }
    }

    private fun Document.toSMangaDetails(): SManga = SManga.create().apply {
        title = selectFirst("h1.md-title")?.text()?.trim() ?: error("title not found")
        thumbnail_url = selectFirst("meta[property=\"og:image\"]")?.attr("content")?.ifBlank { null }
        description = selectFirst("div.md-synopsis p")?.text()?.trim()?.ifEmpty { null }
        status = when {
            selectFirst("a.md-badge-ongoing") != null -> SManga.ONGOING
            selectFirst("a.md-badge-done") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.toChapterList(): List<SChapter> = select("#md-chapter-list a.md-ch").mapNotNull { element ->
        val href = element.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
        val name = element.selectFirst(".md-ch-title")?.text()?.trim() ?: return@mapNotNull null
        SChapter.create().apply {
            url = href.encodedPath
            this.name = name
            date_upload = parseRelativeDate(element.selectFirst(".md-ch-meta > span")?.text())
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = chapter.url.trimStart('/').split('/')
        val mangaSlug = segments.getOrNull(1)
        val chapterSlug = segments.getOrNull(2)
        if (mangaSlug.isNullOrEmpty() || chapterSlug.isNullOrEmpty()) {
            throw Exception("Invalid chapter url: ${chapter.url}")
        }

        val path = "$mangaSlug/$chapterSlug"
        PagesCrypto.ensureLoaded(client, baseUrl, chapter.url, preferences)
        val token = PagesCrypto.token(mangaSlug, chapterSlug)
        val headers = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
        val response = client.get("$baseUrl/_c/mangas/$mangaSlug/chapters/$chapterSlug/pages?_=$token", headers)
            .parseAs<PagesResponse>()

        val payload = PagesCrypto.decryptPages(response.encrypted, token, path)
        return payload.pages.mapIndexedNotNull { index, src ->
            if (src.isBlank()) return@mapIndexedNotNull null
            val key = payload.scrambleKeys?.getOrNull(index)?.takeIf { it.isNotEmpty() }
            val imageUrl = if (key != null) "$src#$key" else src
            Page(index, url = imageUrl, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val html = client.get("$baseUrl/tim-kiem").use { it.body.string() }
        val genres = GENRE_BUTTON_RE.findAll(html)
            .map { match ->
                GenreOption(
                    id = match.groupValues[1].toInt(),
                    name = Parser.unescapeEntities(match.groupValues[2], false).trim(),
                )
            }
            .filter { it.name.isNotEmpty() }
            .distinctBy { it.id }
            .toList()

        return genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private fun parseRelativeDate(text: String?): Long {
        if (text.isNullOrBlank()) return 0
        val match = RELATIVE_DATE_RE.find(text) ?: return 0
        val amount = match.groupValues[1].toLongOrNull() ?: return 0
        val unitMillis = when (match.groupValues[2]) {
            "phút" -> 60_000L
            "giờ" -> 3_600_000L
            "ngày" -> 86_400_000L
            "tuần" -> 604_800_000L
            "tháng" -> 2_592_000_000L
            "năm" -> 31_536_000_000L
            else -> return 0
        }
        return System.currentTimeMillis() - amount * unitMillis
    }

    private companion object {
        const val LOGIN_REQUIRED_MESSAGE =
            "Truyện này yêu cầu đăng nhập. Hãy đăng nhập trang web trong WebView của ứng dụng rồi thử lại."
        val GENRE_BUTTON_RE = Regex(
            "toggleGenre\\('(\\d+)'\\)[\\s\\S]*?<span class=\"truncate\">([^<]*)</span>",
        )
        val RELATIVE_DATE_RE = Regex("(\\d+)\\s*(phút|giờ|ngày|tuần|tháng|năm)")
    }
}
