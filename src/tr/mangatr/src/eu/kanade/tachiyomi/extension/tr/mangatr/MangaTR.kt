package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manga-TR Extension — Mihon/Tachiyomi
 *
 * Site         : https://manga-tr.com
 * Eski domain  : manga-tr.net (bozuk eski extension bunu kullanıyordu)
 * CDN          : https://image.mangatr.site
 *
 * ── KORUMALAR ──────────────────────────────────────────────────────────────
 *
 * 1. Cloudflare DDoS koruma (liste/detay sayfaları)
 *    → cloudflareClient ile otomatik handle edilir.
 *    → İlk açılışta Mihon → "WebView'ı Aç" ile CF'yi bir kez çöz.
 *
 * 2. Özel sürükle-bırak captcha (OKUMA sayfaları)
 *    → Cloudflare değil, sitenin kendi custom captcha'sı.
 *    → Her yeni bölüm açıldığında çıkabilir.
 *    → ÇÖZÜM: WebView'da bölümü bir kez aç, captcha'yı çöz,
 *      sonra Tachiyomi'ye geri dön → sonraki isteklerde cookie taşınır.
 *
 * ── URL YAPISI ─────────────────────────────────────────────────────────────
 *
 * Manga liste  : /manga-list-sayfala.html?sort=views&sort_type=DESC&page=N
 * Manga detay  : /manga-{slug}.html
 * Bölüm okuma  : /id-{chapterID}-read-{slug}-chapter-{number}.html
 * Bölüm listesi: AJAX — /cek/bolum-listesi.php?manga={slug}&bolum_sayfa=1
 */
class MangaTR : ParsedHttpSource() {

    override val name = "Manga-TR"
    override val baseUrl = "https://manga-tr.com"
    override val lang = "tr"
    override val supportsLatest = true

    private val cdnUrl = "https://image.mangatr.site"

    // ── HTTP ─────────────────────────────────────────────────────────────────
    override val client: OkHttpClient = network.cloudflareClient
        .newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 8) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36",
        )

    // ── POPÜLER ──────────────────────────────────────────────────────────────
    override fun popularMangaRequest(page: Int): Request = GET(
        "$baseUrl/manga-list-sayfala.html?sort=views&sort_type=DESC&page=$page&listType=pagination",
        headers,
    )

    override fun popularMangaSelector() =
        "div.col-md-2, div.col-sm-3, div.manga-item, div:has(>h3>a[href*='/manga-'])"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleAnchor = element.selectFirst("h3 a, h4 a")
            ?: element.selectFirst("a[href*='/manga-']")!!
        title = titleAnchor.text().trim()
        setUrlWithoutDomain(titleAnchor.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
        }
    }

    override fun popularMangaNextPageSelector() =
        "ul.pagination li.next a, a[rel='next'], a.next_page"

    // ── SON GÜNCELLENENLER ───────────────────────────────────────────────────
    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/index.html" else "$baseUrl/index-sayfa-$page.html"
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() =
        "div.col-md-1, div.col-sm-2, div:has(>img[title]):has(a[href*='/manga-'])"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val mangaAnchor = element.selectFirst("a[href*='/manga-']")!!
        title = element.selectFirst("img")?.attr("title")?.trim()
            ?: mangaAnchor.text().trim()
        setUrlWithoutDomain(mangaAnchor.attr("abs:href"))
        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("abs:src").ifBlank { img.attr("abs:data-src") }
        }
    }

    override fun latestUpdatesNextPageSelector() =
        "ul.pagination a[href*='index-sayfa-'], a.next_page"

    // ── ARAMA ────────────────────────────────────────────────────────────────
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga-list-sayfala.html".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("listType", "pagination")

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("name", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    urlBuilder.addQueryParameter("sort", filter.toSortValue())
                    urlBuilder.addQueryParameter("sort_type", "DESC")
                }
                is StatusFilter -> filter.toUriPart()?.let {
                    urlBuilder.addQueryParameter("durum", it)
                }
                is ContentTypeFilter -> filter.toUriPart()?.let {
                    urlBuilder.addQueryParameter("icerik", it)
                }
                is GenreFilter -> {
                    filter.state.filter { it.state }.forEach {
                        urlBuilder.addQueryParameter("tur[]", it.value)
                    }
                }
                else -> {}
            }
        }

        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ── MANGA DETAY ──────────────────────────────────────────────────────────
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")?.text()?.trim() ?: ""

        thumbnail_url = document.selectFirst(
            "img[src*='image.mangatr.site'], img[src*='$cdnUrl']",
        )?.attr("abs:src")
            ?: document.selectFirst("img[title]")?.attr("abs:src")

        description = document.selectFirst(
            "div.summary, p.summary, div.konu, div#tab1 p",
        )?.text()?.trim()

        author = document.selectFirst("a[href*='?author=']")?.text()?.trim()

        artist = document.select("a[href*='?artist=']")
            .joinToString { it.text().trim() }
            .ifBlank { null }

        genre = document.select("a[href*='?tur=']")
            .joinToString { it.text().trim() }

        val durum = document.selectFirst("a[href*='?durum=']")?.attr("href") ?: ""
        status = when {
            durum.contains("durum=2") -> SManga.ONGOING
            durum.contains("durum=1") -> SManga.COMPLETED
            document.body().text().contains("Devam Ediyor") -> SManga.ONGOING
            document.body().text().contains("Tamamlandı") -> SManga.COMPLETED
            document.body().text().contains("Askıda") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ── BÖLÜM LİSTESİ ────────────────────────────────────────────────────────
    // Bölümler AJAX ile geliyor: /cek/bolum-listesi.php?manga={slug}&bolum_sayfa=1
    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url
            .removePrefix("/")
            .removePrefix("manga-")
            .removeSuffix(".html")
        return GET("$baseUrl/cek/bolum-listesi.php?manga=$slug&bolum_sayfa=1", headers)
    }

    override fun chapterListSelector() = "table tr:has(a[href*='-read-'])"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val anchor = element.selectFirst("a[href*='-read-']")!!
        name = anchor.text().trim()
        setUrlWithoutDomain(anchor.attr("abs:href"))

        date_upload = element.select("td").lastOrNull()
            ?.text()?.trim()
            ?.let { parseDate(it) } ?: 0L

        chapter_number = anchor.attr("href")
            .substringAfterLast("chapter-")
            .removeSuffix(".html")
            .toFloatOrNull() ?: -1f
    }

    private val dateFormats = listOf(
        SimpleDateFormat("dd.MM.yyyy", Locale("tr")),
        SimpleDateFormat("dd/MM/yyyy", Locale("tr")),
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
        SimpleDateFormat("dd MMMM yyyy", Locale("tr")),
    )

    private fun parseDate(text: String): Long {
        for (fmt in dateFormats) {
            try { return fmt.parse(text)!!.time } catch (e: Exception) {} // ktlint-disable
        }
        return 0L
    }

    // ── SAYFA LİSTESİ (Okuma) ────────────────────────────────────────────────
    override fun pageListParse(document: Document): List<Page> {
        // 1. Doğrudan DOM'dan resimler
        val imgs = document.select(
            "div#page-container img, div.reading-content img, " +
                "div.chapter-content img, div.content-wraper img",
        )
        if (imgs.isNotEmpty()) {
            return imgs.mapIndexed { i, img ->
                val url = img.attr("abs:src")
                    .ifBlank { img.attr("abs:data-src") }
                    .ifBlank { img.attr("abs:data-lazy-src") }
                Page(i, imageUrl = url)
            }
        }

        // 2. JS değişkeninden resimler
        val scripts = document.select("script").joinToString("\n") { it.html() }
        val arrayRegex = Regex(
            """(?:images?|pages?|imgList|chapter_images?)\s*[=:]\s*\[([^\]]{20,})\]""",
            RegexOption.IGNORE_CASE,
        )
        arrayRegex.find(scripts)?.let { match ->
            val urls = Regex(""""(https?://[^"]+\.(jpg|jpeg|png|webp|gif)[^"]*?)"""")
                .findAll(match.groupValues[1])
                .map { it.groupValues[1] }
                .toList()
            if (urls.isNotEmpty()) {
                return urls.mapIndexed { i, url -> Page(i, imageUrl = url) }
            }
        }

        return emptyList()
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        headersBuilder().set("Referer", "$baseUrl/").build(),
    )

    // ── FİLTRELER ────────────────────────────────────────────────────────────
    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        ContentTypeFilter(),
        GenreFilter(),
    )

    private class SortFilter : Filter.Select<String>(
        "Sıralama",
        arrayOf("Görüntülenme", "Son Güncelleme", "Ad"),
    ) {
        fun toSortValue() = when (state) {
            0 -> "views"
            1 -> "last_update"
            2 -> "name"
            else -> "views"
        }
    }

    private class StatusFilter : Filter.Select<String>(
        "Yayın Durumu",
        arrayOf("Tümü", "Tamamlandı", "Devam Ediyor"),
    ) {
        fun toUriPart() = when (state) {
            1 -> "1"
            2 -> "2"
            else -> null
        }
    }

    private class ContentTypeFilter : Filter.Select<String>(
        "İçerik Türü",
        arrayOf("Tümü", "Manga", "Novel", "Webtoon", "Anime"),
    ) {
        fun toUriPart() = when (state) {
            1 -> "1"
            2 -> "2"
            3 -> "3"
            4 -> "4"
            else -> null
        }
    }

    private class Genre(name: String, val value: String) : Filter.CheckBox(name)

    private class GenreFilter : Filter.Group<Genre>(
        "Türler",
        listOf(
            Genre("Action", "Action"), Genre("Adventure", "Adventure"),
            Genre("Comedy", "Comedy"), Genre("Drama", "Drama"),
            Genre("Fantasy", "Fantasy"), Genre("Horror", "Horror"),
            Genre("Isekai", "Isekai"), Genre("Magic", "Magic"),
            Genre("Manhwa", "Manhwa"), Genre("Manhua", "Manhua"),
            Genre("Martial", "Martial"), Genre("Mecha", "Mecha"),
            Genre("Mystery", "Mystery"), Genre("Romance", "Romance"),
            Genre("School", "School"), Genre("Sci-fi", "Sci_fi"),
            Genre("Seinen", "Seinen"), Genre("Shoujo", "Shoujo"),
            Genre("Shounen", "Shounen"), Genre("Slice of life", "Slice of life"),
            Genre("Supernatural", "Supernatural"), Genre("Thriller", "Thriller"),
            Genre("Tragedy", "Tragedy"), Genre("Webtoon", "Webtoon"),
        ),
    )
}
