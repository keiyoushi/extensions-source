package eu.kanade.tachiyomi.extension.ja.mangakoma

import eu.kanade.tachiyomi.multisrc.liliana.FilterData
import eu.kanade.tachiyomi.multisrc.liliana.Liliana
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.ZonedDateTime

// The site runs the same CMS with its NetTruyen layout, whose markup differs from the theme's
@Source
abstract class MangaKoma : Liliana() {

    override suspend fun getPopularManga(page: Int): MangasPage = popularMangaParse(client.get("$baseUrl/all-manga/$page/?sort=views", Fetch.DOCUMENT).asJsoup())

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("figcaption h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("div.image img")?.absUrl("data-original")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.active + li a"

    override suspend fun getLatestUpdates(page: Int): MangasPage = popularMangaParse(client.get("$baseUrl/all-manga/$page/?sort=latest-updated", Fetch.DOCUMENT).asJsoup())

    override fun parseFilters(document: Document) = FilterData(
        genreName = document.selectFirst("div.form-group:has(div.genre-item) > label")?.text() ?: "",
        genres = document.select("div.genre-item").map { it.text() to it.selectFirst("span[data-id]")!!.attr("data-id") },
        chapterCountName = document.label("select-minchapter"),
        chapterCounts = document.options("select-minchapter"),
        statusName = document.label("select-status"),
        statuses = document.options("select-status"),
        genderName = document.label("select-gender"),
        genders = document.options("select-gender"),
        sortName = document.label("select-sort"),
        sorts = document.options("select-sort"),
    )

    private fun Document.label(id: String) = selectFirst("label[for=$id]")?.text() ?: ""

    private fun Document.options(id: String) = select("#$id > option").map { it.text() to it.attr("value") }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = "/manga/$slug" }
        // An unknown series redirects to the home page
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("article#item-detail") == null) return null
        return mangaDetailsParse(document).apply {
            this.url = manga.url
            initialized = true
        }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("article#item-detail h1.title-detail")!!.text()
        thumbnail_url = document.selectFirst("div.col-image img")?.absUrl("src")
        author = document.selectFirst("li.author p.col-xs-8")?.text()?.takeUnless { it.isEmpty() || it == "未詳" }
        genre = document.select("li.kind p.col-xs-8 a").joinToString { it.text() }.ifEmpty { null }
        status = when (document.selectFirst("li.status p.col-xs-8")?.text()) {
            "進行中" -> SManga.ONGOING
            "完了" -> SManga.COMPLETED
            "保留" -> SManga.ON_HIATUS
            "キャンセル" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("div.detail-content > p"))
            .build()
    }

    override fun chapterListSelector() = "#nt_listchapter li.row:has(div.chapter a)"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val link = element.selectFirst("div.chapter a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        name = link.text()
        date_upload = parseRelativeDate(element.selectFirst("div.col-xs-4")?.text())
    }

    // The site only shows how long ago a chapter came out
    private fun parseRelativeDate(text: String?): Long {
        val amount = text?.let { NUMBER_REGEX.find(it) }?.value?.toLongOrNull() ?: return 0L
        val now = ZonedDateTime.now(ZONE)
        val date = when {
            "秒" in text -> now.minusSeconds(amount)
            "分" in text -> now.minusMinutes(amount)
            "時間" in text -> now.minusHours(amount)
            "週間" in text -> now.minusWeeks(amount)
            "日" in text -> now.minusDays(amount)
            "月" in text -> now.minusMonths(amount)
            "年" in text -> now.minusYears(amount)
            else -> return 0L
        }
        return date.toInstant().toEpochMilli()
    }

    override fun pageListParse(document: Document) = document.select("div.page-chapter img").mapIndexed { index, image ->
        Page(index, imageUrl = image.absUrl("data-original").ifEmpty { image.absUrl("src") })
    }

    companion object {
        private val NUMBER_REGEX = Regex("""\d+""")
        private val ZONE = ZoneId.of("Asia/Tokyo")
    }
}
