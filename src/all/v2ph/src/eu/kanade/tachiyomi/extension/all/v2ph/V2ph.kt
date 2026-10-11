package eu.kanade.tachiyomi.extension.all.v2ph

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class V2ph : KeiSource() {

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        add("Accept-Language", "en-US,en;q=0.9")
    }

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int) = popularMangaParse(
        client.get(
            "$baseUrl/category/best-quality?page=$page",
        ).asJsoup(),
    )

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(".albums-list .card").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/?page=$page").asJsoup()
        val mangas = document.select("#latest-albums-title ~ .albums-list .card").mapNotNull(::mangaFromElement)
        val hasNextPage = document.selectFirst("ul.pagination li.page-item a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ===============================

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
        val url = if (query.isNotBlank()) {
            "$baseUrl/search/".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build().toString()
        } else {
            val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart().orEmpty()
            val country = filters.firstInstanceOrNull<CountryFilter>()?.toUriPart().orEmpty()

            when {
                category.isNotEmpty() -> "$baseUrl/category/$category?page=$page"
                country.isNotEmpty() -> "$baseUrl/country/$country?page=$page"
                else -> "$baseUrl/category/best-quality?page=$page"
            }
        }

        return popularMangaParse(client.get(url).asJsoup())
    }

    // ============================== MangaUpdate ==============================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(
            getMangaUrl(manga),
        ).checkPaywall().asJsoup()

        return SMangaUpdate(parseDetails(doc), parseChapters(doc))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.location())
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("[property=og:image]")?.attr("content")

        author = document.selectFirst("dl dt:contains(Vendor) + dd a")?.text()
        artist = document.selectFirst("dl dt:contains(Model) + dd a")?.text()
        genre = document.select("dl dt:contains(Tags) + dd a").joinToString { it.text() }

        val photosCount = document.selectFirst("dl dt:contains(Photos) + dd")?.text()
        val intro = document.selectFirst(".album-intro")?.text()

        description = buildString {
            if (photosCount != null) {
                append("Photos: $photosCount\n\n")
            }
            intro?.let(::append)
        }.trim()

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun parseChapters(document: Document) = listOf(
        SChapter.create().apply {
            name = "Gallery"
            setUrlWithoutDomain(document.location())
            date_upload = dateFormat.tryParseDate(
                document.selectFirst("dl dt:contains(Date) + dd")?.text(),
            )
        },
    )

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(
            getChapterUrl(chapter),
        ).checkPaywall().asJsoup()

        val photosCount = document.selectFirst("dl dt:contains(Photos) + dd")?.text()?.toIntOrNull() ?: 0
        val isGuest = document.selectFirst("a[href*='/login'], a[href*='/register']") != null

        if (isGuest && photosCount > 20) {
            throw Exception("V2PH Session expired. Please log in via WebView to view more than 20 images.")
        }

        val pages = document.select(".photos-list img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }

        val maxPage = (photosCount + 9) / 10

        val extra = coroutineScope {
            (2..maxPage).map { i ->
                async {
                    val sep = if (chapter.url.contains("?")) "&" else "?"
                    client.get("$baseUrl${chapter.url}${sep}page=$i", ensureSuccess = false).use { r ->
                        if (!r.isSuccessful) {
                            emptyList()
                        } else {
                            r.asJsoup().select(".photos-list img").map { it.attr("abs:src") }
                        }
                    }
                }
            }.awaitAll().flatten()
        }
        return pages + extra.map { Page(pages.size, imageUrl = it) }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Text Search ignores the filters below."),
        Filter.Header("If both Category and Country are set, Category takes precedence."),
        Filter.Separator(),
        CategoryFilter(),
        CountryFilter(),
    )

    // ============================= Utilities =============================
    private fun Response.checkPaywall(): Response {
        if (request.url.encodedPath.startsWith("/user/")) {
            throw Exception("This album requires a V2PH premium account. Open in WebView to upgrade.")
        }
        return this
    }

    private fun mangaFromElement(element: Element): SManga? {
        val link = element.selectFirst("a.media-cover") ?: return null
        val titleEl = element.selectFirst(".card-body h6 a") ?: return null
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = titleEl.text()
            thumbnail_url = element.selectFirst(".card-cover img")?.attr("abs:src")
        }
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
