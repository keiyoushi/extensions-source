package eu.kanade.tachiyomi.multisrc.koi

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import keiyoushi.utils.ui.askPassword
import kotlinx.serialization.json.JsonElement
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

abstract class Koi : KeiSource() {

    protected open val mangaPath = "truyen-tranh"

    protected open val popularPath = "nhieu-xem-nhat"

    // Filter name to the home page tab that lists its options
    protected open val filterTabs = listOf(
        "Thể loại" to "#nav-tags",
        "Nhóm" to "#nav-teams",
        "Loạt Truyện" to "#nav-series",
        "Từ khóa" to "#nav-hashtags",
    )

    protected open val chapterMangaLinkSelector = "#post-category-link"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::coverFallback)
        .rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/$popularPath/", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("ul.most-views.single-list-comic li.position-relative")
            .mapNotNull(::mangaFromListItem)
        return MangasPage(mangas, hasNextPage = false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return mangaGridParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val body = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            val response = client.post("$baseUrl/wp-admin/admin-ajax.php", Fetch.XHR, body)
            // Kept from the old sources: the endpoint can answer with a list page instead of JSON
            if ("application/json" !in response.header("Content-Type").orEmpty()) {
                return mangaListParse(response.asJsoup())
            }
            val mangas = response.parseAs<SearchResponse>().data
                .filter { "/$mangaPath/" in it.link }
                .map { result ->
                    SManga.create().apply {
                        title = result.title
                        setUrlWithoutDomain(result.link)
                        thumbnail_url = result.cover?.toCoverUrl()
                    }
                }
                .distinctBy { it.url }
            return MangasPage(mangas, hasNextPage = false)
        }

        val filterPath = filters.filterIsInstance<UriPartFilter>().firstNotNullOfOrNull { it.toUriPart() }
            ?: return getLatestUpdates(page)
        return getFilterMangaList(filterPath, page)
    }

    // Archive pages hold their whole list and page it with JavaScript; only grid pages have /page/N/
    protected open suspend fun getFilterMangaList(path: String, page: Int): MangasPage {
        val url = if (page == 1) baseUrl + path else "$baseUrl${path.trimEnd('/')}/page/$page/"
        return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun mangaListParse(document: Document): MangasPage {
        val items = document.select("#archive-list-table li.position-relative")
            .ifEmpty { document.select("ul.single-list-comic li.position-relative") }
        if (items.isEmpty()) return mangaGridParse(document)

        return MangasPage(items.mapNotNull(::mangaFromListItem), document.hasNextPage())
    }

    private fun mangaGridParse(document: Document) = MangasPage(
        document.select(".col-md-3.col-xs-6.comic-item").mapNotNull(::mangaFromGridItem),
        document.hasNextPage(),
    )

    private fun mangaFromListItem(element: Element): SManga? {
        val link = element.selectFirst("p.super-title a[href*=/$mangaPath/]") ?: return null
        return SManga.create().apply {
            title = link.text()
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = element.selectFirst("img.list-left-img")?.coverUrl()
        }
    }

    private fun mangaFromGridItem(element: Element): SManga? {
        val link = element.selectFirst("a[href*=/$mangaPath/]") ?: return null
        val mangaTitle = element.selectFirst("h3.comic-title")?.textOrNull()
            ?: link.attrOrNull("title")
            ?: return null
        return SManga.create().apply {
            title = mangaTitle
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = element.selectFirst("img")?.coverUrl()
        }
    }

    private fun Document.hasNextPage() = selectFirst("ul.pager li.next:not(.disabled) a") != null

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaUrl = if (url.pathSegments.first() == mangaPath) {
            url
        } else {
            client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst(chapterMangaLinkSelector)
                ?.absUrl("href")
                ?.toHttpUrlOrNull()
                ?.takeIf { it.pathSegments.first() == mangaPath }
                ?: return null
        }
        if (mangaUrl.pathSegments.getOrNull(1).isNullOrEmpty()) return null

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaUrl.toString()) }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document).apply { url = manga.url },
            chapters = if (fetchChapters) chapterListParse(document) else chapters,
        )
    }

    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h2.info-title")!!.text()
        thumbnail_url = document.selectFirst(".comic-intro img.img-thumbnail")?.coverUrl()
        author = document.selectFirst("strong:contains(Tác giả) + span")?.text()?.takeUnless(::isPlaceholder)
        genre = document.select(".comic-info .tags a[href*=/the-loai/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        status = parseStatus(document.selectFirst("span.comic-stt")?.text())
        // Only long synopses get the "Xem Thêm" wrapper, short ones sit bare next to the follow button
        description = parseDescription(document.selectFirst(".intro-container"))
    }

    protected fun parseDescription(synopsis: Element?): String? {
        synopsis?.select(".follow-container, .hide-long-text-shadow")?.remove()
        return MarkdownDescription.Builder()
            .description(synopsis?.takeUnless { isPlaceholder(it.text()) })
            .build()
            // Some uploaders paste Markdown: drop its heading marks, bold marks and closing rule
            ?.replace(pastedMarkdownRegex, "")
            ?.trim()
            ?.removeSurrounding("\"")
    }

    protected fun parseStatus(status: String?): Int {
        val normalized = status?.lowercase() ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in normalized -> SManga.ONGOING
            "hoàn thành" in normalized || "trọn bộ" in normalized -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    protected fun isPlaceholder(text: String) = text.isEmpty() || placeholders.any { it.equals(text, ignoreCase = true) }

    // ============================== Chapters ==============================

    protected open suspend fun chapterListParse(document: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visited = mutableSetOf(document.location())
        var page: Document? = document

        while (page != null) {
            chapters += page.select(".chapter-table table tbody tr").mapNotNull(::chapterFromElement)
            val nextUrl = page.selectFirst("ul.pager li.next:not(.disabled) a[href]:not([href=#])")?.absUrl("href")
            page = if (nextUrl != null && visited.add(nextUrl)) client.get(nextUrl, Fetch.DOCUMENT).asJsoup() else null
        }

        return chapters.distinctBy { it.url }
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val link = element.selectFirst("a.text-capitalize[href]") ?: return null
        val rawName = link.selectFirst("span.hidden-sm.hidden-xs")?.text() ?: link.text()
        val chapterName = chapterNameRegex.find(rawName)?.value
            ?.replace(chapterWordRegex, "Chap")
            ?.replace(multiSpaceRegex, " ")
            ?: rawName.substringAfterLast("–").substringAfterLast("-").trim().ifEmpty { rawName }

        return SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = if (element.isLocked()) "🔒 $chapterName" else chapterName
            date_upload = parseChapterDate(element.selectFirst("td.hidden-xs.hidden-sm")?.text())
        }
    }

    protected fun Element.isLocked() = selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

    private fun parseChapterDate(date: String?): Long = dateFormatLong.tryParseDate(date, dateZone).takeIf { it != 0L }
        ?: dateFormatShort.tryParseDate(date, dateZone)

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        var html = fetchChapterHtml(chapterUrl)
        if (LOGIN_NOTICE in html) {
            throw Exception("Chương này cần đăng nhập vào tài khoản phù hợp bằng webview để xem")
        }

        var document = html.asJsoup(chapterUrl)
        val lockForm = document.selectFirst("form.post-password-form")
        if (lockForm != null) {
            val hint = (
                document.selectFirst("p:contains(Gợi ý)")
                    ?: document.selectFirst("*:containsOwn(Gợi ý)")?.parent()
                )?.text()?.replaceFirst(hintColonRegex, "$1\n")?.trim()
            val password = askPassword(
                title = chapter.name,
                message = listOfNotNull("Chương này yêu cầu mật khẩu", hint).joinToString("\n\n"),
                hint = "Mật khẩu",
            ) ?: throw Exception("Đã hủy nhập mật khẩu")

            val postAction = lockForm.absUrl("action").ifEmpty { "$baseUrl/wp-login.php?action=postpass" }
            val postHeaders = headers.newBuilder()
                .set("Referer", chapterUrl)
                .build()
            val formBody = FormBody.Builder()
                .add("post_password", password)
                .add("redirect_to", chapterUrl)
                .add("Submit", "Nhập")
                .build()
            client.post(postAction, postHeaders, Fetch.DOCUMENT, formBody, ensureSuccess = false).close()

            html = fetchChapterHtml(chapterUrl)
            document = html.asJsoup(chapterUrl)
            if (document.selectFirst("form.post-password-form") != null) {
                throw Exception("Mật khẩu không chính xác")
            }
        }

        return ImageDecryptor.extractImageUrls(html, document).mapIndexed { index, imageUrl ->
            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }
    }

    // Locked pages are cacheable (max-age, no Vary: Cookie), so skip the disk cache
    private suspend fun fetchChapterHtml(chapterUrl: String): String = client.get(chapterUrl, Fetch.DOCUMENT, CacheControl.FORCE_NETWORK).use { it.body.string() }

    // =============================== Filters ==============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return filterTabs.map { (_, selector) ->
            document.select("$selector a[href]")
                .mapNotNull { link ->
                    val name = link.textOrNull() ?: return@mapNotNull null
                    val path = link.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
                    FilterOption(name, path)
                }
                .distinctBy { it.path }
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(filterTabs.map { it.first }, data?.parseAs<List<List<FilterOption>>>())

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("h3.blue-title")
        .firstOrNull { it.text().equals("Truyện liên quan", ignoreCase = true) }
        ?.nextElementSibling()
        ?.select(".comic-item, .comic-item-box")
        ?.mapNotNull(::mangaFromGridItem)
        ?.distinctBy { it.url }
        .orEmpty()

    // =============================== Covers ===============================

    protected fun Element.coverUrl(): String? = absUrl("data-lazy-src")
        .ifEmpty { absUrl("data-src") }
        .ifEmpty { absUrl("src") }
        .takeUnless { it.isEmpty() || it.startsWith("data:") }
        ?.toCoverUrl()

    // Pages link small WordPress thumbnails: ask for the original and keep the thumbnail in the fragment for coverFallback
    protected fun String.toCoverUrl(): String {
        val original = replace(thumbnailSizeRegex, "")
        if (original == this) return this
        return original.toHttpUrlOrNull()?.newBuilder()
            ?.fragment(COVER_FALLBACK_PREFIX + this)
            ?.build()
            ?.toString()
            ?: this
    }

    // Not every upload keeps its original: try the 720x970 size next, then the thumbnail itself
    private fun coverFallback(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        val thumbnail = request.url.fragment
            ?.takeIf { it.startsWith(COVER_FALLBACK_PREFIX) }
            ?.removePrefix(COVER_FALLBACK_PREFIX)
            ?: return response

        for (fallbackUrl in listOf(thumbnail.replace(thumbnailSizeRegex, "-720x970"), thumbnail).distinct()) {
            if (response.code != 401 && response.code != 404) break
            response.close()
            response = chain.proceed(request.newBuilder().url(fallbackUrl).build())
        }
        return response
    }

    companion object {
        private const val COVER_FALLBACK_PREFIX = "fallback-url:"
        private val thumbnailSizeRegex = Regex("""-\d+x\d+(?=\.[a-zA-Z]+$)""")

        private const val LOGIN_NOTICE = "đăng nhập</strong> để xem nội dung chương này"
        private val placeholders = listOf("Đang cập nhật", "Đang cập nhật...", "Không có")

        private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
        private val dateFormatLong = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        private val dateFormatShort = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)

        private val chapterNameRegex = Regex("""chap\s*\d+(?:\.\d+)?""", RegexOption.IGNORE_CASE)
        private val chapterWordRegex = Regex("chap", RegexOption.IGNORE_CASE)
        private val multiSpaceRegex = Regex("""\s+""")
        private val pastedMarkdownRegex = Regex("""^\\#{1,6}[ \t]*|\\?-{3,}\s*\z|\\\*\\\*""", RegexOption.MULTILINE)
        private val hintColonRegex = Regex("""(Gợi\s*ý\s*:?)\s*""", RegexOption.IGNORE_CASE)
    }
}
