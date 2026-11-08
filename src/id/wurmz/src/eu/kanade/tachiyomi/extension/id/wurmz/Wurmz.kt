package eu.kanade.tachiyomi.extension.id.wurmz

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class Wurmz : KeiSource() {

    private val rscHeaders: Headers
        get() = headersBuilder().add("Rsc", "1").build()

    // ======================== Popular ========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/semua-komik?sort=popular_all&page=$page", Fetch.DOCUMENT).asJsoup()
        return mangaListParse(document)
    }

    // ======================== Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/semua-komik?sort=update&page=$page", Fetch.DOCUMENT).asJsoup()
        return mangaListParse(document)
    }

    // ======================== Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/semua-komik".toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> addQueryParameter("sort", filter.toUriPart())
                    is TypeFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("type", filter.toUriPart())
                    is StatusFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("status", filter.toUriPart())
                    is GenreFilter -> if (filter.toUriPart().isNotEmpty()) addQueryParameter("genre", filter.toUriPart())
                    else -> {}
                }
            }
        }.build()

        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return mangaListParse(document)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select("article.comic-card").mapNotNull { element ->
            val link = element.selectFirst("a[href*=/detail/]") ?: return@mapNotNull null
            val titleText = element.selectFirst("h2")?.text()?.ifEmpty { null }
                ?: link.attr("aria-label").ifEmpty { null }
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.attr("abs:href"))
                title = titleText
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst("a:contains(Berikutnya)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details & Updates =============
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = getMangaUrl(manga).toHttpUrl().newBuilder().addQueryParameter("_rsc", "").build()
        val bodyString = client.get(url, rscHeaders, Fetch.XHR).body.string()
        val details = mangaDetailsParse(bodyString).apply { this.url = manga.url }
        val chapterList = chapterListParse(bodyString, manga.url)

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val baseHost = baseUrl.toHttpUrl().host.removePrefix("www.")
        if (!url.host.removePrefix("www.").equals(baseHost, ignoreCase = true)) return null

        val detailIdx = url.pathSegments.indexOf("detail")
        if (detailIdx == -1 || url.pathSegments.size < detailIdx + 3) return null

        val type = url.pathSegments[detailIdx + 1]
        val slug = url.pathSegments[detailIdx + 2]
        if (type.isEmpty() || slug.isEmpty()) return null

        val manga = SManga.create().apply { this.url = "/detail/$type/$slug" }
        return runCatching {
            fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }.getOrNull()
    }

    private fun mangaDetailsParse(bodyString: String): SManga {
        val details = bodyString.extractNextJsRsc<MangaDetailsDto> {
            it is JsonObject && it["@type"]?.jsonPrimitive?.content == "ComicSeries"
        } ?: bodyString.extractNextJsRsc<JsonObject> {
            it is JsonObject && it.containsKey("dangerouslySetInnerHTML") &&
                it.jsonObject["dangerouslySetInnerHTML"]?.jsonObject?.get("__html")?.jsonPrimitive?.content?.contains("ComicSeries") == true
        }?.get("dangerouslySetInnerHTML")?.jsonObject?.get("__html")?.jsonPrimitive?.content?.let { html ->
            runCatching { html.parseAs<MangaDetailsDto>() }.getOrNull()
        } ?: extractComicSeriesJson(bodyString)
            ?: throw Exception("Gagal memproses detail komik")

        val statusText = bodyString.extractNextJsRsc<JsonObject> {
            it is JsonObject && it.containsKey("className") && it["className"]?.jsonPrimitive?.content == "status-badge"
        }?.get("children")?.jsonPrimitive?.content
            ?: statusBadgeRegex.find(bodyString)?.groupValues?.get(1)

        return details.toSManga().apply {
            status = parseStatus(statusText.orEmpty())
        }
    }

    private fun extractComicSeriesJson(body: String): MangaDetailsDto? {
        val json = comicSeriesRegex.find(body)?.value ?: return null
        return runCatching { json.parseAs<MangaDetailsDto>() }.getOrNull()
    }

    private val comicSeriesRegex = Regex("""\{"@context":"https://schema\.org","@type":"ComicSeries".*?\}(?=[0-9a-f]+:|[\r\n]|$)""")

    private val statusBadgeRegex = Regex(""""className":"status-badge","children":"([^"]+)"""")

    private fun parseStatus(status: String) = when (status.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "tamat", "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ONGOING
        "drop" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ======================== Chapters =======================
    private fun chapterListParse(bodyString: String, mangaUrl: String): List<SChapter> {
        val chapterList = bodyString.extractNextJsRsc<ChapterListDto> {
            it is JsonObject && it.containsKey("chapters")
        } ?: throw Exception("Gagal memproses daftar chapter")

        val slug = chapterList.sourceSlug
            ?: mangaUrl.removePrefix("/detail/").substringBefore("/chapter")

        val chapters = chapterList.chapters.map { it.toSChapter(slug) }
        val updatedDate = updatedDateRegex.find(bodyString)?.groupValues?.get(1)
        if (updatedDate != null) {
            chapters.firstOrNull()?.date_upload = parseDate(updatedDate)
        }
        return chapters
    }

    private fun parseDate(dateStr: String): Long {
        val normalized = dateStr.lowercase()
        if (normalized == "baru saja" || normalized == "just now") {
            return System.currentTimeMillis()
        }

        val amount = numberRegex.find(normalized)?.value?.toIntOrNull()
        if (amount != null) {
            val calendar = Calendar.getInstance()
            when {
                "detik" in normalized || "second" in normalized -> calendar.add(Calendar.SECOND, -amount)
                "menit" in normalized || "minute" in normalized -> calendar.add(Calendar.MINUTE, -amount)
                "jam" in normalized || "hour" in normalized -> calendar.add(Calendar.HOUR_OF_DAY, -amount)
                "hari" in normalized || "day" in normalized -> calendar.add(Calendar.DAY_OF_YEAR, -amount)
                "minggu" in normalized || "week" in normalized -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
                "bulan" in normalized || "month" in normalized -> calendar.add(Calendar.MONTH, -amount)
                "tahun" in normalized || "year" in normalized -> calendar.add(Calendar.YEAR, -amount)
                else -> return DATE_FORMAT.tryParseDate(dateStr)
            }
            return calendar.timeInMillis
        }

        return DATE_FORMAT.tryParseDate(dateStr)
    }

    private val numberRegex = Regex("""\d+""")
    private val updatedDateRegex = Regex(""""Diperbarui\s*",\s*"([^"]+)"""")

    // ======================== Pages ==========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = getChapterUrl(chapter).toHttpUrl().newBuilder().addQueryParameter("_rsc", "").build()
        val pageList = client.get(url, rscHeaders, Fetch.XHR).extractNextJs<PageListDto> {
            it is JsonObject && it.containsKey("images")
        } ?: throw Exception("Gagal memproses daftar gambar")

        return pageList.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ======================== Filters ========================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("id")).withZone(ZoneId.of("Asia/Jakarta"))
