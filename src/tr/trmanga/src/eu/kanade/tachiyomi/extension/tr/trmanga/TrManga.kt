@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.tr.trmanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

@Source
abstract class TrManga : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/webtoon-listesi?sort=views&short_type=DESC&page=$page").asJsoup()
        return mangaListFromDocument(document)
    }

    private fun mangaListFromDocument(document: Document): MangasPage {
        val mangas = document.select("[data-more-list=wl] > a").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("span.line-clamp-2")?.text() ?: element.attr("title")
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/son-eklenenler?page=$page").asJsoup()
        val mangas = document.select("article:has(h3 a)").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("h3 a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val shortTypeFilter = filters.firstInstanceOrNull<OrderFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()

        val url = "$baseUrl/webtoon-listesi".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            val genre = genreFilter?.toUriPart().orEmpty()
            if (genre.isNotEmpty()) {
                addQueryParameter("genre", genre)
            }
            val status = statusFilter?.toUriPart().orEmpty()
            if (status == "uptodate") {
                addQueryParameter("uptodate", "1")
            } else if (status.isNotEmpty()) {
                addQueryParameter("status", status)
            }
            val sort = sortFilter?.toUriPart().orEmpty()
            if (sort.isNotEmpty()) {
                addQueryParameter("sort", sort)
            }
            val shortType = shortTypeFilter?.toUriPart().orEmpty()
            if (shortType.isNotEmpty()) {
                addQueryParameter("short_type", shortType)
            }
        }.build()

        val document = client.get(url).asJsoup()
        return mangaListFromDocument(document)
    }

    // ============================== Deeplink ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null
        val segments = url.pathSegments
        if (segments.firstOrNull() != "webtoon" || segments.size < 2) return null
        val mangaUrl = "$baseUrl/webtoon/${segments[1]}"
        val document = client.get(mangaUrl).asJsoup()
        return mangaDetailsFromDocument(document).apply {
            setUrlWithoutDomain(mangaUrl)
        }
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = mangaDetailsFromDocument(document)
        val chapterList = chapterListFromDocument(document)
        return SMangaUpdate(details, chapterList)
    }

    private fun mangaDetailsFromDocument(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1#wt-title")!!.text()
        author = document.selectFirst("dt:containsOwn(Yazar) + dd")?.text()
        artist = author
        genre = document.select("a.badge[href^=/tur/]").joinToString { it.text() }.takeIf { it.isNotEmpty() }
        status = document.selectFirst("dt:containsOwn(Durum) + dd")?.text()?.parseStatus() ?: SManga.UNKNOWN
        description = document.selectFirst("#wtiDesc")?.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("abs:content")
    }

    private val statusOngoing = listOf("ongoing", "devam ediyor", "güncel")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    private fun String.parseStatus(): Int = when (this.lowercase()) {
        in statusOngoing -> SManga.ONGOING
        in statusCompleted -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun chapterListFromDocument(document: Document): List<SChapter> = document.select("#chapters > li.wt-ch").map { chapterFromElement(it) }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        val number = element.attr("data-chapter")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = buildString {
            append("Bölüm ", number)
            element.selectFirst(".wt-ch-n")?.text()?.takeIf { it.isNotEmpty() }?.let { append(" - ", it) }
        }
        chapter_number = number.toFloatOrNull() ?: -1f
        val meta = element.selectFirst(".wt-ch-m")
        scanlator = meta?.select("a.fs-chip")?.joinToString { it.text() }?.takeIf { it.isNotEmpty() }
        date_upload = meta?.ownText()?.substringBefore("·")?.let { parseRelativeDate(it) } ?: 0L
    }

    private fun parseRelativeDate(date: String): Long {
        val number = NUMBER_REGEX.find(date)?.groupValues?.getOrNull(0)?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("yıl") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("ay") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("hafta") -> cal.apply { add(Calendar.WEEK_OF_MONTH, -number) }.timeInMillis
            date.contains("gün") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("saat") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("dakika") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("saniye") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        if (document.selectFirst("*:containsOwn(Üyelere Özel)") != null) {
            throw Exception("Bu bölüm üyelere özeldir. Okumak için WebView üzerinden giriş yapın")
        }
        return document.select("#images img.reader-img").mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") })
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    companion object {
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
