package eu.kanade.tachiyomi.extension.vi.truyenhentai18

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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TruyenHentai18 : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/xem-nhieu-nhat", page)))

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.col-6.col-md-4.col-lg-2.mb-3").map { element ->
            val link = element.selectFirst("a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = element.selectFirst("h2")!!.text()
                thumbnail_url = element.selectFirst("img")?.let(::imageUrl)
            }
        }

        return MangasPage(mangas, document.selectFirst("ul.pagination li a:contains(»)") != null)
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaPage(client.get(buildPagedUrl("/moi-cap-nhat", page)))

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val selectedGenre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        val url = if (query.isBlank() && !selectedGenre.isNullOrEmpty()) {
            buildPagedUrl("/category/$selectedGenre", page).toHttpUrl()
        } else {
            buildPagedUrl("", page).toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
        }

        return parseMangaPage(client.get(url))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val pathSegments = url.pathSegments
        val mangaPath = when {
            pathSegments.size == 1 && pathSegments[0].endsWith(".html") -> url.encodedPath
            pathSegments.size == 2 &&
                pathSegments[0].isNotEmpty() &&
                pathSegments[1].startsWith("chapter-") &&
                pathSegments[1].endsWith(".html") -> "/${pathSegments[0]}.html"
            else -> return null
        }

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = true).manga
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.manga-cover")?.absUrl("src")
            ?: document.selectFirst(".card img.img-fluid")?.absUrl("src")
        genre = document.select("a.badge.bg-primary")
            .map(Element::text)
            .takeIf { it.isNotEmpty() }
            ?.joinToString()

        document.select(".list-group-item, div").forEach { element ->
            val text = element.text()
            val normalizedText = text.lowercase()
            when {
                "trạng thái:" in normalizedText -> {
                    status = when {
                        "hoàn thành" in normalizedText -> SManga.COMPLETED
                        "đang tiến hành" in normalizedText -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                }
                "tác giả:" in normalizedText -> author = text.substringAfter(":").trim()
            }
        }

        description = document.select(".description")
            .map { it.wholeText().trim() }
            .takeIf { it.isNotEmpty() }
            ?.joinToString("\n")
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.chapter-item").map { element ->
        val link = element.selectFirst("a.fw-bold")!!
        SChapter.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = parseRelativeDate(element.selectFirst("div.chapter-date")?.text())
        }
    }

    private fun parseRelativeDate(value: String?): Long {
        val dateText = value?.substringAfterLast("•")?.trim()?.lowercase() ?: return 0L
        if ("trước" !in dateText) return 0L

        val amount = dateNumberRegex.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val duration = when {
            "giây" in dateText -> amount.seconds
            "phút" in dateText -> amount.minutes
            "giờ" in dateText || "tiếng" in dateText -> amount.hours
            "ngày" in dateText -> amount.days
            "tuần" in dateText -> (amount * 7).days
            "tháng" in dateText -> (amount * 30).days
            "năm" in dateText -> (amount * 365).days
            else -> return 0L
        }

        return (Clock.System.now() - duration).toEpochMilliseconds()
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("div#viewer.chapter-container img")
        .mapIndexed { index, element ->
            Page(index, imageUrl = imageUrl(element))
        }

    private fun imageUrl(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        else -> element.attr("abs:src")
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val categoryMenu = client.get(baseUrl).asJsoup()
            .selectFirst("#categoryDropdown")
            ?.nextElementSibling()

        val genres = categoryMenu
            ?.select("a[href*=/category/]")
            .orEmpty()
            .mapNotNull { link ->
                val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val pathSegments = link.absUrl("href").toHttpUrlOrNull()?.pathSegments
                    ?: return@mapNotNull null
                val categoryIndex = pathSegments.indexOf("category")
                val slug = pathSegments.getOrNull(categoryIndex + 1)
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                GenreOption(name, slug)
            }
            .distinctBy { it.slug }

        return if (genres.isEmpty()) {
            genres.toJsonElement()
        } else {
            listOf(GenreOption("Tất cả", ""), *genres.toTypedArray()).toJsonElement()
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    private val dateNumberRegex = Regex("\\d+")
}
