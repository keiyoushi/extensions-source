package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DuaLeoTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(5)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-tranh-hot?page=$page").asJsoup())

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-moi-cap-nhat?page=$page").asJsoup())

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .build()
            return mangaListParse(client.get(url).asJsoup())
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        return if (genrePath != null) {
            mangaListParse(client.get("$baseUrl$genrePath?page=$page").asJsoup())
        } else {
            getPopularManga(page)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select(".main_menu .sub_menu a[href*=/the-loai/]")
        .mapNotNull { element ->
            val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val path = element.absUrl("href").toHttpUrl().encodedPath
            GenreOption(name, path)
        }
        .distinctBy { it.uriPart }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // ============================== List ==================================

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select(".box_list .li_truyen").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href*=/truyen-tranh/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = linkElement.selectFirst(".name")!!.text()
        thumbnail_url = linkElement.selectFirst(".img img")?.absUrl("data-src")
            ?: linkElement.selectFirst(".img img")?.absUrl("src")
    }

    // ============================== Details ===============================

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst(".box_info_right h1")!!.text()
        genre = document.select(".list-tag-story a")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = document.selectFirst(".story-detail-info")
            ?.text()
            ?.ifEmpty { null }
        status = parseStatus(
            document.select(".info-item")
                .firstOrNull { it.text().contains("Tình trang") }
                ?.text(),
        )
        thumbnail_url = document.selectFirst(".box_info_left .img img")?.absUrl("src")
    }

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized -> SManga.COMPLETED
            "đang cập nhật" in normalized -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document): List<SChapter> = document
        .select(".chapter-item")
        .map { element ->
            SChapter.create().apply {
                val linkElement = element.selectFirst(".chap_name a")!!
                setUrlWithoutDomain(linkElement.absUrl("href"))
                name = linkElement.text()
                date_upload = parseDate(element.selectFirst(".chap_update")?.text())
            }
        }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return runCatching {
            LocalDate.parse(dateStr, dateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

        return document.select(".content_view_chap img")
            .mapNotNull { img ->
                val dataImg = img.attr("data-img")
                val src = img.absUrl("src")

                when {
                    dataImg.isNotBlank() -> decryptImageUrl(dataImg)
                    src.isNotBlank() && !src.startsWith("data:") -> src
                    else -> null
                }
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private fun decryptImageUrl(url: String): String? {
        val lastSlashIndex = url.lastIndexOf('/')
        val dotIndex = url.lastIndexOf('.')
        if (lastSlashIndex == -1 || dotIndex == -1 || dotIndex <= lastSlashIndex) return null

        val basePath = url.substring(0, lastSlashIndex + 1)
        val encodedName = url.substring(lastSlashIndex + 1, dotIndex)
        val extension = url.substring(dotIndex)

        val base64 = encodedName.replace('-', '+').replace('_', '/')
        val decoded = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Exception) {
            return url
        }

        val decrypted = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor decryptSalt[i % decryptSalt.length].code).toByte()
        }

        val decryptedName = String(decrypted, Charsets.UTF_8)
        return "$basePath$decryptedName$extension"
    }

    private val decryptSalt = "dualeo_salt_2025"
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
