package eu.kanade.tachiyomi.extension.vi.teletruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TeleTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/danh-sach/xem-nhieu?page=$page"))

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/danh-sach/truyen-moi-cap-nhat?page=$page"))

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedSlug()
        val url = when {
            query.isNotBlank() -> "$baseUrl/tim-kiem-nang-cao?keyword=${query.encodeUrl()}&page=$page"
            genre != null -> "$baseUrl/the-loai/$genre?page=$page"
            else -> "$baseUrl/tim-kiem-nang-cao?page=$page"
        }
        return parseMangaList(client.get(url))
    }

    // ============================== Filter ================================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("a[href*='/the-loai/']")
        .mapNotNull { link ->
            val slug = link.attr("href").substringAfterLast("/the-loai/").substringBefore("?")
            slug.takeIf { it.isNotBlank() }?.let { GenreOption(link.text(), it) }
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return if (genres.isEmpty()) FilterList() else FilterList(GenreFilter(genres))
    }

    @Serializable
    class GenreOption(
        val name: String,
        val slug: String,
    )

    class GenreFilter(options: List<GenreOption>) : Filter.Select<String>("Thể loại", options.map { it.name }.toTypedArray()) {
        private val slugs = options.map { it.slug }

        fun selectedSlug(): String? = slugs.getOrNull(state)
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.firstOrNull()?.takeIf { it !in supportedPaths } ?: return null
        val manga = SManga.create().apply { setUrlWithoutDomain("/$slug") }
        return fetchMangaUpdate(manga, emptyList(), true, true).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h2.info-title")!!.text()
        author = document.selectFirst("a[href*='/tac-gia/']")?.text()
        status = document.selectFirst(".comic-intro-text strong:matchesOwn(^Tình trạng:) + span")?.text()?.let {
            if (it.contains("Đang tiến hành")) {
                SManga.ONGOING
            } else if (it.contains("Đã hoàn thành")) {
                SManga.COMPLETED
            } else {
                SManga.UNKNOWN
            }
        } ?: SManga.UNKNOWN
        genre = document.select(".comic-info a[href*='/the-loai/']")
            .distinctBy { it.attr("href") }
            .joinToString { it.text() }
        thumbnail_url = document.selectFirst(".comic-info")?.parent()
            ?.selectFirst(".col-sm-4 > img")
            ?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }
        description = document.selectFirst("div.hide-long-text")?.apply {
            selectFirst(".hide-long-text-shadow")?.remove()
        }?.text()?.trim()
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table tr").mapNotNull { row ->
        val link = row.selectFirst("a[href*='/chuong-']") ?: return@mapNotNull null
        val chapterName = chapterNameRegex.find(link.text())?.value ?: return@mapNotNull null
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = chapterName
            date_upload = dateFormat.tryParseDate(row.selectFirst("td.hidden-xs.hidden-sm")?.text(), dateZone)
        }
    }

    // ================================ Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        val imageUrls = document.select("#view-chapter img[data-src]").mapNotNull { image ->
            image.absUrl("data-src").takeIf { it.isNotBlank() }
        }
        return imageUrls
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val heading = document.select("h3.blue-title")
            .firstOrNull { it.text().equals("Truyện liên quan", ignoreCase = true) }
            ?: return emptyList()
        return heading.parent()?.select(".comic-item")?.mapNotNull { card ->
            val link = card.selectFirst("a[href]:has(h3.comic-title)") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }
            }
        }.orEmpty()
    }

    // ============================== Utilities =============================

    private fun parseMangaList(response: okhttp3.Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".comic-item").mapNotNull { card ->
            val link = card.selectFirst("a[href]:has(h3.comic-title)") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.let { it.absUrl("data-src").ifBlank { it.absUrl("src") } }
            }
        }
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        return MangasPage(mangas, document.selectFirst("a[href*='page=${currentPage + 1}']") != null)
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterNameRegex = Regex("Chap\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
    private val supportedPaths = setOf("danh-sach", "tim-kiem-nang-cao", "the-loai", "random")
}
