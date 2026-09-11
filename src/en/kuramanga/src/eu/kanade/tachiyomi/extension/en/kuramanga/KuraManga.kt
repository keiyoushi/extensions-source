package eu.kanade.tachiyomi.extension.en.kuramanga

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class KuraManga : KeiSource() {

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("section:has(h2:contains(Popular)) a.sp-card").mapNotNull { element ->
            val titleEl = element.selectFirst(".sp-cap h3") ?: return@mapNotNull null
            SManga.create().apply {
                title = titleEl.text()
                url = "/" + element.attr("href").removePrefix("/")
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pageUrl = if (page > 1) "$baseUrl/?page=$page" else "$baseUrl/"
        val document = client.get(pageUrl).asJsoup()
        val mangas = document.select(".update-list .update-row").mapNotNull { element ->
            val link = element.selectFirst("a.update-series-link") ?: return@mapNotNull null
            SManga.create().apply {
                title = link.text()
                url = "/" + link.attr("href").removePrefix("/")
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("a[data-lu-next]:not(.is-disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("ajax", "1")
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("name", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        val genres = filter.state
                            .filter { it.state }
                            .map { it.name }
                        if (genres.isNotEmpty()) {
                            addQueryParameter("genre", genres.joinToString(","))
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("status", filter.vals[filter.state].replace(" ", "_").lowercase())
                        }
                    }
                    is AdultFilter -> {
                        if (!filter.state) {
                            addQueryParameter("adult", "0")
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        val searchHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
        val response = client.get(url, searchHeaders).parseAs<SearchResponse>()

        val mangas = response.data.map { it.toSManga() }
        val hasNextPage = response.data.size == PAGE_SIZE && (response.total == 0 || page * PAGE_SIZE < response.total)
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.firstOrNull { it.isNotBlank() } ?: return null
        if (slug in setOf("search", "assets", "api", "login", "register")) return null
        val document = client.get("$baseUrl/$slug").asJsoup()
        mangaDetailsParse(document).apply {
            this.url = "/$slug"
        }
    }.getOrNull()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document).apply {
                url = manga.url
            },
            chapters = chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.manga-title")!!.text()
        description = document.selectFirst(".summary-inner")?.text()
            ?: document.selectFirst(".mp-synopsis")?.text()
        val storyAndArt = document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Story & Art)) .mp-cred-v")?.text()
            ?: document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Author & Artist)) .mp-cred-v")?.text()
        author = document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Author)) .mp-cred-v")?.text()
            ?: document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Story)) .mp-cred-v")?.text()
            ?: storyAndArt
            ?: document.selectFirst(".meta-grid div:contains(Author:)")?.text()?.substringAfter("Author:")?.trim()
        artist = document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Artist)) .mp-cred-v")?.text()
            ?: document.selectFirst(".mp-cred:has(.mp-cred-k:contains(Art)) .mp-cred-v")?.text()
            ?: storyAndArt
            ?: document.selectFirst(".meta-grid div:contains(Artist:)")?.text()?.substringAfter("Artist:")?.trim()
        genre = document.select(".genre-list a.genre-chip").joinToString { it.text() }
        status = (
            document.selectFirst(".mp-status")?.text()
                ?: document.selectFirst(".meta-grid div:contains(Status:)")?.text()?.substringAfter("Status:")
            )?.trim()?.lowercase().parseStatus()
        thumbnail_url = document.selectFirst("meta[property='og:image']")?.attr("content")
        initialized = true
    }

    private fun String?.parseStatus(): Int = when (this) {
        "ongoing", "upcoming" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on_hold", "on hold", "hiatus" -> SManga.ON_HIATUS
        "canceled", "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    private fun chapterListParse(document: Document): List<SChapter> {
        return document.select(".chapter-list .chapter-item").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            SChapter.create().apply {
                name = link.text()
                url = "/" + link.attr("href").removePrefix("/")
                date_upload = dateFormat.tryParseDate(element.selectFirst("time")?.text())
            }
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#chapterImages img").mapIndexed { index, img ->
            val imageUrl = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        StatusFilter("Status", statusList),
        AdultFilter("Include Adult Content"),
        GenreFilter("Genres", genreNames.map { Genre(it) }),
    )

    // ============================== Private ===============================
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)

    companion object {
        private const val PAGE_SIZE = 18
    }
}
