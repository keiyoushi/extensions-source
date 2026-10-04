package eu.kanade.tachiyomi.extension.ar.hentailek

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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId

@Source
abstract class HentaiLek : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBrowse(page, "", FilterList(SortFilter("popular")))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchBrowse(page, "", FilterList(SortFilter("latest")))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchBrowse(page, query, filters)

    private suspend fun fetchBrowse(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
        } else {
            val type = filters.firstInstanceOrNull<TypeFilter>()?.value.orEmpty()
            val status = filters.firstInstanceOrNull<StatusFilter>()?.value.orEmpty()
            val genre = filters.firstInstanceOrNull<GenreFilter>()?.value.orEmpty()
            val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "latest"

            "$baseUrl/library".toHttpUrl().newBuilder()
                .addQueryParameter("sort", sort)
                .apply {
                    if (type.isNotEmpty()) addQueryParameter("type", type)
                    if (status.isNotEmpty()) addQueryParameter("status", status)
                    if (genre.isNotEmpty()) addQueryParameter("genre", genre)
                }
        }
        builder.addQueryParameter("page", page.toString())

        val document = client.get(builder.build()).asJsoup()
        return MangasPage(
            document.parseMangaList(),
            document.selectFirst("a[rel=next]") != null,
        )
    }

    private fun Document.parseMangaList(): List<SManga> = select("a[href]")
        .mapNotNull { element ->
            val url = element.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
            val segments = url.pathSegments
            if (segments.size != 2 || segments[0] != "manga") return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(url.encodedPath)
                title = element.selectFirst("h3")?.text()?.trim().orEmpty()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
            }
        }
        .filter { it.title.isNotBlank() }
        .distinctBy { it.url }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.size != 2 || url.pathSegments[0] != "manga") return null

        return client.get(url).asJsoup().parseDetails(url.encodedPath)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(document.parseDetails(manga.url), document.parseChapters())
    }

    private fun Document.parseDetails(mangaUrl: String): SManga = SManga.create().apply {
        url = mangaUrl
        title = selectFirst("h1.h-hero")?.text()?.trim().orEmpty()
        thumbnail_url = selectFirst("img[src*=cover]")?.absUrl("src")?.takeIf { it.isNotBlank() }
        description = section("الملخّص")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        author = info("المؤلّف")
        artist = info("الرسّام")
        status = when (info("الحالة")) {
            "مستمرّة" -> SManga.ONGOING
            "مكتملة" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        genre = (genres() + listOfNotNull(info("النوع"))).joinToString(", ")
        initialized = true
    }

    private fun Document.section(label: String): Element? = selectFirst("h3.h-sub:contains($label)")?.nextElementSibling()

    private fun Document.info(label: String): String? {
        val container = section("معلومات") ?: return null
        return container.select("div")
            .firstOrNull { it.selectFirst("dt")?.text()?.trim() == label }
            ?.selectFirst("dd")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun Document.genres(): List<String> {
        val container = section("معلومات") ?: return emptyList()
        val dd = container.select("div")
            .firstOrNull { it.selectFirst("dt")?.text()?.trim() == "التصنيفات" }
            ?.selectFirst("dd")
            ?: return emptyList()

        return dd.select("a[href*=genres]").map { it.text().trim() }.filter { it.isNotEmpty() }
    }

    // =============================== Chapters =============================

    private fun Document.parseChapters(): List<SChapter> = select("div.overflow-hidden a[href]").mapNotNull { element ->
        val url = element.absUrl("href").toHttpUrlOrNull() ?: return@mapNotNull null
        val segments = url.pathSegments
        if (segments.size != 3 || segments[0] != "manga" || !segments[2].startsWith("chapter-")) {
            return@mapNotNull null
        }

        SChapter.create().apply {
            setUrlWithoutDomain(url.encodedPath)
            name = element.selectFirst("span.min-w-0")?.text()?.trim()
                ?.takeIf { it.isNotEmpty() } ?: element.text().trim()
            date_upload = element.selectFirst("span.text-xs.text-muted")?.text().parseArabicDate()
        }
    }.distinctBy { it.url }

    // ================================ Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("div.reader-page img")
        .mapIndexedNotNull { index, element ->
            element.absUrl("src").takeIf { it.isNotBlank() }?.let { Page(index, imageUrl = it) }
        }

    // =============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList = hentaiLekFilters()
}

private val ARABIC_MONTHS = mapOf(
    "يناير" to 1,
    "فبراير" to 2,
    "مارس" to 3,
    "أبريل" to 4,
    "ابريل" to 4,
    "مايو" to 5,
    "يونيو" to 6,
    "يوليو" to 7,
    "أغسطس" to 8,
    "اغسطس" to 8,
    "سبتمبر" to 9,
    "أكتوبر" to 10,
    "اكتوبر" to 10,
    "نوفمبر" to 11,
    "ديسمبر" to 12,
)

private val DATE_REGEX = Regex("""(\d{1,2})\s+(\S+)\s+(\d{4})""")

private fun String?.parseArabicDate(): Long {
    val match = DATE_REGEX.find(this ?: return 0L) ?: return 0L
    val day = match.groupValues[1].toIntOrNull() ?: return 0L
    val month = ARABIC_MONTHS[match.groupValues[2]] ?: return 0L
    val year = match.groupValues[3].toIntOrNull() ?: return 0L

    return runCatching {
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(0L)
}
