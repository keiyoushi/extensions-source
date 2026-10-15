package eu.kanade.tachiyomi.extension.tr.mangadusleri

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
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Mangadusleri : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

    private val loginCheckInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.request.url.toString().contains("register.php") ||
            response.request.url.toString().contains("login")
        ) {

            response.close()

            throw IOException("Bu içeriği görüntülemek için lütfen WebView'ı (küre simgesini) açın ve giriş yapın!")
        }

        response
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(loginCheckInterceptor)
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter()))

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList())

    // ============================== Search ================================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val order = filters.firstInstanceOrNull<SortFilter>()?.toUriPart()
        val genre = filters.firstInstanceOrNull<GenresFilter>()?.toUriPart()

        val url = if (query.isNotBlank()) {
            "$baseUrl$SEARCH_PATH".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
        } else {
            if (genre != null) {
                "$baseUrl$GENRE_PATH".toHttpUrl().newBuilder()
                    .addQueryParameter("name", genre)
                    .addQueryParameter("page", page.toString())
                    .build()
            } else {
                "$baseUrl$BROWSE_PATH".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())
                    .apply {
                        if (order != null) {
                            addQueryParameter("sort", order)
                        }
                    }
                    .build()
            }
        }

        return parseBrowsePage(client.get(url))
    }

    // ============================== Manga List ============================

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".recent-grid .recent-card").mapNotNull { element ->
            val mangaLink = element.selectFirst("a, .recent-card-body a") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst(".recent-card-body a .recent-card-title")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                title = mangaTitle
                thumbnail_url = element.selectFirst("img.recent-card-img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("a.page-link:contains(İleri)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Details ===============================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(parseDetails(doc), parseChapters(doc))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.info-col h1")!!.text()
        thumbnail_url = document.selectFirst("img.manga-cover-big")?.absUrl("src")
        genre = document.select("a[href*=genre.php] span")
            .joinToString { it.text().trim() }
            .ifEmpty { null }
        author = document.select("div:containsOwn(Yazar)").first()?.nextElementSibling()?.text()
        artist = document.select("div:containsOwn(Çizer)").first()?.nextElementSibling()?.text()
        description = document.selectFirst("div.info-col .manga-summary-box")?.text()?.trim()
        status = parseStatus(document.selectFirst(".status-badge"))
        setUrlWithoutDomain(document.location())
    }

    private fun parseStatus(element: Element?): Int = when {
        element?.hasClass("status-ongoing") == true -> SManga.ONGOING
        element?.hasClass("status-completed") == true -> SManga.COMPLETED

        element?.text()?.contains("Devam Ediyor", ignoreCase = true) == true -> SManga.ONGOING
        element?.text()?.contains("Tamamlandı", ignoreCase = true) == true -> SManga.COMPLETED

        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    private fun parseChapters(document: Document) = document.select("#chapterList a.chapter-grid-item-new").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))

            name = element.selectFirst("div > span")!!.text().trim()

            val dateText = element.select("div > span").last()?.text()

            dateText?.let { date_upload = parseDate(it) }
        }
    }.reversed()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val images = document.select("img[alt*='Manga Sayfası'], img[alt*='Resmi']")

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("data-lazy-src") }
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?.normalizeImageUrl()
                ?: return@mapIndexedNotNull null

            Page(index, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?) = getFilters()

    // ============================== Helpers ===============================

    private fun String.normalizeImageUrl(): String = if (startsWith("//")) "https:$this" else this

    private fun parseDate(dateStr: String): Long = dateFormat.tryParseDate(dateStr)

    companion object {
        private const val SEARCH_PATH = "/search.php"
        private const val BROWSE_PATH = "/latest.php"
        private const val GENRE_PATH = "/genre.php"
    }
}
