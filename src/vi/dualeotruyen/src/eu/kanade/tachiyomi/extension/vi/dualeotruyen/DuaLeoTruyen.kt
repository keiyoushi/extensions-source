package eu.kanade.tachiyomi.extension.vi.dualeotruyen

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
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

    override suspend fun getPopularManga(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-tranh-hot?page=$page", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = mangaListParse(client.get("$baseUrl/truyen-moi-cap-nhat?page=$page", Fetch.DOCUMENT).asJsoup())

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
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen-tranh") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen-tranh/$slug")
        }
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
            manga = mangaDetailsParse(document, manga),
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = SManga.create().apply {
        url = manga.url
        title = document.selectFirst(".box_info_right h1")!!.text()
        genre = document.select(".list-tag-story a")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(document.selectFirst(".story-detail-info"))
            .field("Nhóm dịch", document.infoItem("Nhóm dịch"))
            .altTitles(document.infoItem("Tên Khác")?.split(",").orEmpty(), "Tên khác")
            .build()
        status = parseStatus(document.infoItem("Tình trang"))
        thumbnail_url = document.selectFirst(".box_info_left .img img")?.absUrl("src")
    }

    private fun Document.infoItem(label: String): String? = selectFirst(".info-item:containsOwn($label:)")
        ?.text()
        ?.substringAfter(':')

    private fun parseStatus(statusText: String?): Int {
        val normalized = statusText?.lowercase(Locale.ROOT)

        return when {
            normalized == null -> SManga.UNKNOWN
            "hoàn thành" in normalized || "full" in normalized -> SManga.COMPLETED
            "đang cập nhật" in normalized -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val config = V7Config.extract(document)
            ?: throw Exception("Không tìm thấy cấu hình giải mã trang")

        var canvasIndex = 0
        return document.select("img, canvas")
            .mapNotNull { element ->
                val token = when (element.normalName()) {
                    "img" -> if (element.hasClass(config.imageClass) && element.hasAttr(config.attrA)) {
                        runCatching { config.imageToken(element) }.getOrNull()
                    } else {
                        null
                    }
                    "canvas" -> if (element.hasClass(config.canvasClass) && element.hasAttr(config.attrA)) {
                        // Canvas data attributes are decoys; the real token comes
                        // from the config, indexed in document order from the end.
                        runCatching { config.canvasToken(canvasIndex) }.getOrNull()
                            ?.also { canvasIndex++ }
                    } else {
                        null
                    }
                    else -> null
                } ?: return@mapNotNull null

                config.decodeObfuscatedUrl(token)
            }
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select(".main_menu .sub_menu a[href*=/the-loai/]")
        .mapNotNull { element ->
            val name = element.textOrNull() ?: return@mapNotNull null
            val path = element.absUrl("href").toHttpUrl().encodedPath
            GenreOption(name, path)
        }
        .distinctBy { it.uriPart }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
