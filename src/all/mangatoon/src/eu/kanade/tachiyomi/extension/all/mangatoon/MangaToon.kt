package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

open class MangaToon(
    final override val lang: String,
    private val urlLang: String = lang,
) : ParsedHttpSource() {

    override val name = "MangaToon (Limited)"

    override val baseUrl = "https://mangatoon.mobi"

    override val id: Long = when (lang) {
        "pt-BR" -> 2064722193112934135
        else -> super.id
    }

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(1, 1, TimeUnit.SECONDS)
        .build()

    private val locale by lazy { Locale.forLanguageTag(lang) }

    private val lockedError = when (lang) {
        "pt-BR" ->
            "Este capítulo é pago e não pode ser lido. " +
                "Use o app oficial do MangaToon para comprar e ler."
        else ->
            "This chapter is paid and can't be read. " +
                "Use the MangaToon official app to purchase and read it."
    }

    override fun popularMangaRequest(page: Int): Request {
        // Portuguese website doesn't seen to have popular titles.
        val path = if (lang == "pt-BR") "comic" else "hot"

        return GET("$baseUrl/$urlLang/genre/$path?type=1&page=${page - 1}", headers)
    }

    override fun popularMangaSelector() = "div.genre-content div.items a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.content-title").text().trim()
        thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun popularMangaNextPageSelector() = "span.next"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/$urlLang/genre/new?type=1&page=${page - 1}", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val searchUrl = "$baseUrl/$urlLang/search".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaSelector() = "div.comics-result div.recommend-item:has(a[abs:href^=$baseUrl])"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("div.recommend-comics-title").text().trim()
        thumbnail_url = element.select("img").imgAttr().toNormalPosterUrl()
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        author = document.select("div.detail-author-name span").text()
            .substringAfter(": ")
        description = document.select("div.detail-description-short p")
            .joinToString("\n\n") { it.text() }
        genre = document.select("div.detail-tags-info span").text()
            .split("/")
            .map { it.capitalize(locale) }
            .sorted()
            .joinToString { it.trim() }
        status = document.select("div.detail-status").text().trim().toStatus()
        val thumbnail = document.select("div.detail-img img").imgAttr().toNormalPosterUrl()
        if (!thumbnail.contains("cartoon-big-images")) {
            thumbnail_url = thumbnail
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "/episodes", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList = super.chapterListParse(response)

        // Finds the last free chapter to filter the paid ones from the list.
        // The desktop website doesn't indicate which chapters are paid in
        // the title page, and the mobile API is heavily encrypted.
        val firstPaid = PAID_CHECK_BREAKPOINTS.find { breakpoint ->
            if (breakpoint > chapterList.size) {
                return@find false
            }

            val pageListRequest = pageListRequest(chapterList[breakpoint - 1])
            val pageListResponse = client.newCall(pageListRequest).execute()

            runCatching { pageListParse(pageListResponse) }
                .getOrDefault(emptyList()).isEmpty()
        }

        return chapterList
            .let { if (firstPaid != null) it.take(firstPaid - 1) else it }
            .reversed()
    }

    override fun chapterListSelector() = "a.episode-item-new"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("div.episode-title-new:last-child").text().trim()
        chapter_number = element.select("div.episode-number").text().trim()
            .toFloatOrNull() ?: -1f
        date_upload = element.select("div.episode-date span.open-date").text().toDate()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.pictures div img:first-child")
            .mapIndexed { i, element -> Page(i, "", element.imgAttr()) }
            .takeIf { it.isNotEmpty() } ?: throw Exception(lockedError)
    }

    override fun imageUrlParse(document: Document) = ""

    private fun String.toDate(): Long {
        return runCatching { DATE_FORMAT.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    private fun String.toNormalPosterUrl(): String = replace(POSTER_SUFFIX, "$1")

    private fun String.toStatus(): Int = when (lowercase(locale)) {
        in ONGOING_STATUS -> SManga.ONGOING
        in COMPLETED_STATUS -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val ONGOING_STATUS = listOf(
            "连载", "on going", "sedang berlangsung", "tiếp tục cập nhật",
            "en proceso", "atualizando", "เซเรียล", "en cours", "連載中",
        )

        private val COMPLETED_STATUS = listOf(
            "完结",
            "completed",
            "tamat",
            "đã full",
            "terminada",
            "concluído",
            "จบ",
            "fin",
        )

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy-MM-dd", Locale.US)
        }

        private val POSTER_SUFFIX = "(jpg)-poster(.*)\\d+?$".toRegex()

        private val PAID_CHECK_BREAKPOINTS = arrayOf(5, 10, 15, 20)
    }
}
