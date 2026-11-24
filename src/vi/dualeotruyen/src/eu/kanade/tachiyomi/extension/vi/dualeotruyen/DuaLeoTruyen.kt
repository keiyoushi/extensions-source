package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.xor
import keiyoushi.utils.sha256
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DuaLeoTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(5)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-tranh-hot?page=$page", Fetch.DOCUMENT).asJsoup())

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-moi-cap-nhat?page=$page", Fetch.DOCUMENT).asJsoup())

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .build()
            return mangaListParse(client.get(url, Fetch.DOCUMENT).asJsoup())
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        return if (genrePath != null) {
            mangaListParse(client.get("$baseUrl$genrePath?page=$page", Fetch.DOCUMENT).asJsoup())
        } else {
            getPopularManga(page)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
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

    private fun parseDate(dateStr: String?): Long = dateFormat.tryParseDate(dateStr, dateZone)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}", Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}", Fetch.DOCUMENT).asJsoup()
        val config = document.selectFirst("script[data-v]")!!.attr("data-v")
            .reversed()
            .let { Base64.decode(it, Base64.URL_SAFE) }
            .let { it.copyOfRange(1, it.size).lcgXor(it[0].toInt(), 29, 71) }
            .decodeToString()
            .parseAs<PageConfig>()

        val attrs = "[${config.urlAttr}][${config.prefixAttr}][${config.seedAttr}]"
        return document.select("img.${config.imageClass}$attrs, canvas.${config.canvasClass}$attrs")
            .mapNotNull { config.imageUrl(it) }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    private fun PageConfig.imageUrl(element: Element): String? {
        val url = Base64.decode(element.attr(prefixAttr) + element.attr(urlAttr), Base64.URL_SAFE)
            .lcgXor(element.attr(seedAttr).toInt(), multiplier, increment)
            .decodeToString()
            .toHttpUrl()
        val fileName = url.pathSegments.last()
        if (!fileName.startsWith(namePrefix)) return null

        val (version, nonce, salt, mac, cipherText) = Base64.decode(fileName.substringBefore('.').removePrefix(namePrefix), Base64.URL_SAFE)
            .decodeToString()
            .split('|')
        val cipher = Base64.decode(cipherText, Base64.DEFAULT)
        val keyStream = (0..(cipher.size - 1) / 32)
            .map { "$key|STREAM|$nonce|$salt|$it".sha256() }
            .reduce(ByteArray::plus)
        val name = cipher.xor(keyStream).decodeToString()
        if ("$version|$nonce|$salt|$name".hmacSha256(key.encodeToByteArray()).toHexString() != mac) return null

        return url.newBuilder()
            .setPathSegment(url.pathSegments.lastIndex, "$name.${fileName.substringAfter('.')}")
            .build()
            .toString()
    }

    private fun ByteArray.lcgXor(seed: Int, multiplier: Int, increment: Int): ByteArray {
        var state = seed and 0xFF
        return ByteArray(size) { i ->
            state = (state * multiplier + increment) and 0xFF
            (this[i].toInt() xor state).toByte()
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
