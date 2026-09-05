package eu.kanade.tachiyomi.extension.en.hentaikun

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class HentaiKun : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addNetworkInterceptor { chain ->
        val request = chain.request()
        val pathSegments = request.url.pathSegments
        if (pathSegments.firstOrNull() != "manga" || pathSegments.size < 4) {
            return@addNetworkInterceptor chain.proceed(request)
        }

        // Reading type:
        // - One page (1)
        // - All page (2)
        val chapterKey = "${pathSegments[2]}/${pathSegments[3]}"
        val cookies = request.header("Cookie")
            ?.split("; ")
            ?.filterNot { it.startsWith("$chapterKey=") }
            .orEmpty() + "$chapterKey=2"

        chain.proceed(request.newBuilder().header("Cookie", cookies.joinToString("; ")).build())
    }

    // =============================== Popular ================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val pageStr = if (page > 1) "$page/" else ""
        val document = client.get("$baseUrl/manga/manga-list/most-viewed/$pageStr").asJsoup()
        return MangasPage(
            parseTableListing(document),
            hasNextPage = document.selectFirst("ul.pagination li[aria-label=Next]") != null,
        )
    }

    // =============================== Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pageStr = if (page > 1) "$page/" else ""
        val document = client.get("$baseUrl/manga/manga-list/last-updated/$pageStr").asJsoup()
        return MangasPage(
            parseTableListing(document),
            hasNextPage = document.selectFirst("ul.pagination li[aria-label=Next]") != null,
        )
    }

    // =============================== Search =================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val pageStr = if (page > 1) "$page/" else ""
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "title"

        val document = if (query.isNotBlank()) {
            client.get("$baseUrl/manga/search/$searchType/${query.trim()}/$pageStr").asJsoup()
        } else {
            return getPopularManga(page)
        }
        val hasNextPage = document.selectFirst("ul.pagination li[aria-label=Next]") != null

        val mangas = if (document.selectFirst("table.table-striped") != null) {
            parseTableListing(document)
        } else {
            parseGalleryListing(document)
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Manga Details ================================

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.single_title h1")?.text()
            ?: throw Exception("Title not found")

        thumbnail_url = document.selectFirst("meta[property='og:image']")?.absUrl("content")

        author = document.select("h2:has(strong:contains(Artist)) a")
            .joinToString { it.text() }
            .ifEmpty { null }

        val category = document.selectFirst("h2:has(strong:contains(Category)) a")?.text()
        val tags = document.select("div.desc a[href*='/tag/'] span.label-danger")
            .map { it.text() }
        genre = buildList {
            if (category != null) add(category)
            addAll(tags)
        }.joinToString().ifEmpty { null }

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    // ========================= Chapter List =================================

    private fun parseChapterList(document: Document): List<SChapter> = document.select("table a.readchap").map { anchor ->
        SChapter.create().apply {
            name = anchor.text().ifEmpty { "Chapter" }
            setUrlWithoutDomain(anchor.absUrl("href"))
            val row = anchor.closest("tr")
            val dateText = row?.selectFirst("td:last-child h6")?.text()
            date_upload = dateFormat.tryParseDate(dateText)
            chapter_number = chapterNumberRegex.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    // ========================= Page List ====================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val jsonData = document.select("script")
            .firstOrNull { it.data().contains("var jsondata=") }
            ?.data()
            ?.substringAfter("var jsondata=")
            ?.substringBefore(';')
            ?: throw Exception("Could not find any images for this chapter.")

        return jsonData.parseAs<List<String>>().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ========================= Filters =====================================

    override fun getFilterList(data: JsonElement?) = FilterList(
        SearchTypeFilter(),
    )

    // ========================= Helpers =====================================
    private fun parseTableListing(document: Document): List<SManga> {
        return document.select("table.table-striped tr:not(.danger)").mapNotNull { row ->
            val anchor = row.selectFirst("td:first-child a") ?: return@mapNotNull null
            SManga.create().apply {
                title = anchor.text()
                setUrlWithoutDomain(anchor.absUrl("href"))
                thumbnail_url = Jsoup.parseBodyFragment(anchor.attr("title"), baseUrl)
                    .selectFirst("img")?.absUrl("src")
            }
        }
    }

    private fun parseGalleryListing(document: Document): List<SManga> {
        return document.select("div.thumbnail[id^='galary-']").mapNotNull { div ->
            val overlayAnchor = div.selectFirst("div.overlay a") ?: return@mapNotNull null
            SManga.create().apply {
                title = overlayAnchor.text()
                setUrlWithoutDomain(overlayAnchor.absUrl("href"))
                thumbnail_url = div.selectFirst("img.img-responsive")?.absUrl("src")
            }
        }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ROOT)

    companion object {
        private val chapterNumberRegex = Regex("""(\d+(?:\.\d+)?)""")
    }
}
