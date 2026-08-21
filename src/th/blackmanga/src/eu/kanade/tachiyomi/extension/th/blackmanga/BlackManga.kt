package eu.kanade.tachiyomi.extension.th.blackmanga

import android.net.Uri
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class BlackManga : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (page <= 1) "$baseUrl/" else "$baseUrl/page/$page/"
        val response = client.get(url)
        return response.use { parseMangaList(it.asJsoup()) }
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val sep = if (page > 1) "?page=$page&order=update" else "?order=update"
        val url = "$baseUrl/manga/$sep"
        val response = client.get(url, ensureSuccess = false)
        return response.use { parseMangaList(it.asJsoup()) }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        val url = buildString {
            append(baseUrl)

            if (genreFilter != null && genreFilter.state > 0) {
                append("/genres/${genreFilter.toUriPart()}/")
                if (page > 1) append("page/$page/")
                return@buildString
            }

            if (query.isNotBlank()) {
                append("/search/${Uri.encode(query)}/")
                if (page > 1) append("page/$page/")
                val sort = sortFilter?.toUriPart()
                if (!sort.isNullOrEmpty()) append("?order=$sort")
            } else {
                append("/manga/")
                val params = mutableListOf<String>()
                if (page > 1) params += "page=$page"
                sortFilter?.toUriPart()?.takeIf { it.isNotEmpty() }?.let { params += "order=$it" }
                if (statusFilter != null && statusFilter.state > 0) {
                    statusFilter.toUriPart().takeIf { it.isNotEmpty() }?.let { params += "status=$it" }
                }
                if (params.isNotEmpty()) append(params.joinToString("&", prefix = "?"))
            }
        }
        val response = client.get(url, ensureSuccess = false)
        return response.use { parseMangaList(it.asJsoup()) }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val response = client.get(url.toString(), ensureSuccess = false)
        if (response.code != 200) {
            response.close()
            return null
        }
        return response.use { mangaDetailsParse(it.asJsoup()).apply { this.url = url.encodedPath } }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = baseUrl + manga.url
        val response = client.get(url, ensureSuccess = false)
        return response.use {
            val doc = it.asJsoup()
            val sManga = if (fetchDetails) {
                mangaDetailsParse(doc).apply { this.url = manga.url }
            } else {
                manga
            }
            val sChapters = if (fetchChapters) {
                doc.select("#chapterlist .clstyle li[data-num]")
                    .filter { el -> el.attr("data-num").toFloatOrNull() != null }
                    .mapNotNull(::chapterFromElement)
            } else {
                emptyList()
            }
            SMangaUpdate(sManga, sChapters)
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = baseUrl + chapter.url
        val response = client.get(url, ensureSuccess = false)
        val html = response.use {
            it.body?.string() ?: throw Exception("Empty response body at: $url (code: ${it.code})")
        }

        val readerRegex = Regex("""ts_reader\.run\((\{.*?\})\)""", RegexOption.DOT_MATCHES_ALL)
        val match = readerRegex.find(html)
            ?: throw Exception("ts_reader script not found at: $url")

        val json = Json.parseToJsonElement(match.groupValues[1]).jsonObject

        val images = json["sources"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("images")?.jsonArray
            ?: throw Exception("No images found at: $url")

        return images.mapIndexed { index, imgUrl ->
            Page(index, imageUrl = imgUrl.jsonPrimitive.content)
        }
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".bsx").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".hpage a.r, .pagination a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a[href]")?.let { link ->
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.attr("title")
        }
        element.selectFirst("img")?.let { img ->
            thumbnail_url = img.absUrl("src")
        }
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst("h1.entry-title")?.text()?.let { title = it }

        document.selectFirst(".thumb img")?.let { img ->
            thumbnail_url = img.absUrl("src")
        }

        document.selectFirst(".entry-content-single[itemprop=description]")?.let { content ->
            description = content.text()
        }

        document.select(".infotable tr").forEach { row ->
            val tds = row.select("td")
            if (tds.size == 2) {
                val key = tds[0].text()
                val value = tds[1].text()
                when {
                    "สถานะ" in key -> status = when (value.lowercase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed", "complete", "เสร็จสิ้น" -> SManga.COMPLETED
                        "hiatus", "on hold", "หยุดชั่วคราว" -> SManga.ON_HIATUS
                        "cancelled", "discontinued", "ยกเลิก" -> SManga.CANCELLED
                        else -> SManga.UNKNOWN
                    }
                    "ผู้แต่ง" in key || "นักเขียน" in key -> author = value
                }
            }
        }

        genre = document.select(".seriestugenre a[rel=tag]")
            .map { it.text() }
            .filter { it.isNotEmpty() }
            .joinToString()
    }

    private fun chapterFromElement(element: Element): SChapter? {
        val link = element.selectFirst(".eph-num a") ?: return null
        return SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            val numText = element.attr("data-num")
            chapter_number = numText.toFloatOrNull() ?: 0F
            name = element.selectFirst(".chapternum")?.text()
                ?.takeIf { it.isNotEmpty() }
                ?: "ตอนที่ $numText"
            element.selectFirst(".chapterdate")?.text()?.let { dateStr ->
                date_upload = parseChapterDate(dateStr)
            }
        }
    }

    // region Filters

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(SortFilter(), StatusFilter(), GenreFilter())

    private class SortFilter :
        UriPartFilter(
            "Sort",
            arrayOf(
                "Default" to "",
                "A-Z" to "title",
                "Z-A" to "titlereverse",
                "Latest" to "latest",
                "Most Added" to "added",
                "Popular" to "popular",
                "Updated" to "update",
            ),
        )

    private class StatusFilter :
        UriPartFilter(
            "Status",
            arrayOf(
                "All" to "",
                "Ongoing" to "ongoing",
                "Completed" to "completed",
                "Hiatus" to "hiatus",
            ),
        )

    private class GenreFilter :
        UriPartFilter(
            "Genre",
            arrayOf(
                "All" to "",
                "Action" to "action",
                "Adult" to "adult",
                "Adventure" to "adventure",
                "Comedy" to "comedy",
                "Drama" to "drama",
                "Ecchi" to "ecchi",
                "Fantasy" to "fantasy",
                "Gender Bender" to "gender-bender",
                "Harem" to "harem",
                "Historical" to "historical",
                "Horror" to "horror",
                "Isekai" to "isekai",
                "Josei" to "josei",
                "Martial Arts" to "martial-arts",
                "Mature" to "mature",
                "Mystery" to "mystery",
                "Psychological" to "psychological",
                "Romance" to "romance",
                "School Life" to "school-life",
                "Sci-Fi" to "sci-fi",
                "Seinen" to "seinen",
                "Shoujo" to "shoujo",
                "Shoujo Ai" to "shoujo-ai",
                "Shounen" to "shounen",
                "Slice of Life" to "slice-of-life",
                "Smut" to "smut",
                "Sports" to "sports",
                "Supernatural" to "supernatural",
                "Tragedy" to "tragedy",
            ),
        )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    // endregion

    // region Date Parsing

    private fun parseChapterDate(date: String): Long {
        val lcDate = date.lowercase()

        if (lcDate.endsWith(" ago")) {
            parseRelativeDate(lcDate)?.let { return it }
        }

        when {
            "เมื่อวาน" in lcDate || lcDate.startsWith("yesterday") ->
                return daysAgo(1)
            "วันนี้" in lcDate || lcDate.startsWith("today") ->
                return daysAgo(0)
        }

        var normalized = date
        THAI_MONTHS.forEach { (thai, eng) -> normalized = normalized.replace(thai, eng) }

        for (pattern in DATE_FORMATS) {
            runCatching {
                return java.time.LocalDate.parse(normalized, java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.US))
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
        }
        return 0L
    }

    private fun parseRelativeDate(date: String): Long? {
        val parts = date.split(" ")
        if (parts.size < 3 || parts[2] != "ago") return null
        val number = parts[0].toLongOrNull() ?: return null
        val unit = parts[1].removeSuffix("s")
        val now = java.time.ZonedDateTime.now()
        val adjusted = when (unit) {
            "year", "yr" -> now.minusYears(number)
            "month" -> now.minusMonths(number)
            "week", "wk" -> now.minusWeeks(number)
            "day" -> now.minusDays(number)
            "hour", "hr" -> now.minusHours(number)
            "minute", "min" -> now.minusMinutes(number)
            "second", "sec" -> now.minusSeconds(number)
            else -> return null
        }
        return adjusted.toInstant().toEpochMilli()
    }

    private fun daysAgo(days: Long): Long = java.time.ZonedDateTime.now()
        .minusDays(days)
        .truncatedTo(java.time.temporal.ChronoUnit.DAYS)
        .toInstant()
        .toEpochMilli()

    // endregion
}

private val THAI_MONTHS = mapOf(
    "มกราคม" to "January",
    "กุมภาพันธ์" to "February",
    "มีนาคม" to "March",
    "เมษายน" to "April",
    "พฤษภาคม" to "May",
    "มิถุนายน" to "June",
    "กรกฎาคม" to "July",
    "สิงหาคม" to "August",
    "กันยายน" to "September",
    "ตุลาคม" to "October",
    "พฤศจิกายน" to "November",
    "ธันวาคม" to "December",
)

private val DATE_FORMATS = listOf(
    "d MMMM, yyyy",
    "MMMM d, yyyy",
    "yyyy-MM-dd",
)
