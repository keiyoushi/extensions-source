package eu.kanade.tachiyomi.extension.ja.jestful

import android.util.Base64
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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Jestful : KeiSource() {

    // ================================= Browse ================================
    private fun listUrl(page: Int, sort: String) = "$baseUrl/manga-list.html?page=$page&sort=$sort&sort_type=DESC"

    private fun mangasPageParse(document: Document): MangasPage {
        val mangas = document.select("article.manga-update-card").map { cardFromElement(it) }
        val pageOf = PAGE_REGEX.find(document.selectFirst(".library-sort-page-indicator")?.text().orEmpty())
        val hasNextPage = pageOf?.let { it.groupValues[1].toInt() < it.groupValues[2].toInt() } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    private fun cardFromElement(element: Element): SManga {
        val link = element.selectFirst("a.update-cover-link")!!
        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            title = element.selectFirst(".update-title a")!!.text()
            thumbnail_url = fullResCover(element.selectFirst("img.update-cover-img")?.absUrl("src"))
        }
    }

    // =============================== Popular ===============================
    override suspend fun getPopularManga(page: Int) = mangasPageParse(client.get(listUrl(page, "views"), Fetch.DOCUMENT).asJsoup())

    // ================================ Latest ===============================
    override suspend fun getLatestUpdates(page: Int) = mangasPageParse(client.get(listUrl(page, "last_update"), Fetch.DOCUMENT).asJsoup())

    // ================================= Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga-list.html?".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortBy -> {
                    url.addQueryParameter("sort", filter.sort)
                    url.addQueryParameter("sort_type", filter.sortType)
                }

                is Status -> {
                    if (filter.mStatus.isNotEmpty()) url.addQueryParameter("m_status", filter.mStatus)
                }

                is TextField -> {
                    if (filter.state.isNotBlank()) url.addQueryParameter(filter.key, filter.state)
                }

                is GenreList -> {
                    filter.state.filter { it.isIncluded() }.forEach { url.addQueryParameter("include_genres[]", it.slug) }
                    filter.state.filter { it.isExcluded() }.forEach { url.addQueryParameter("exclude_genres[]", it.slug) }
                }

                else -> {}
            }
        }

        return mangasPageParse(client.get(url.build(), Fetch.DOCUMENT).asJsoup())
    }

    // ============================== Manga Details =============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val fileName = url.encodedPath.substringAfterLast('/')
        val seriesFileName = when {
            fileName.startsWith("hwms-") && fileName.endsWith(".html") -> fileName
            fileName.startsWith("bsaq-") && fileName.endsWith(".html") ->
                "hwms-" + fileName.removePrefix("bsaq-").replace(CHAPTER_SUFFIX_REGEX, ".html")
            else -> return null
        }
        val seriesUrl = "$baseUrl/$seriesFileName"

        val document = client.get(seriesUrl, Fetch.DOCUMENT).asJsoup()
        val title = document.selectFirst(".manga-detail-title")?.text() ?: return null
        return mangaDetailsParse(document).apply {
            setUrlWithoutDomain(seriesUrl)
            this.title = title
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document).apply { title = manga.title },
            chapterListParse(document),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        author = metaValue(document, "Author")
        artist = metaValue(document, "Artist")
        genre = document.select(".genre-tag-pill").joinToString { it.text() }
        status = when (document.selectFirst(".badge-status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = fullResCover(document.selectFirst(".manga-detail-poster img")?.absUrl("src"))
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#synopsis-content")?.text())
            .apply {
                ratingParts(document)?.let { (score, votes) -> rating(score, max = 5, votes = votes) }
                field("Followers", metaValue(document, "Followers"))
                field("Magazine", metaValue(document, "Magazine"))
                field("Views", metaValue(document, "Views"))
                field("Year", metaValue(document, "Released"))
            }
            .altTitles(altTitles(document), label = "Associated Name(s)")
            .build()
    }

    // ================================ Chapters ==============================
    private fun chapterListParse(document: Document): List<SChapter> = document.select("a.chapter-card-item").map { chapterFromElement(it) }.distinctBy { it.url }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst(".chapter-name-text")!!.text()
        date_upload = element.selectFirst(".chapter-meta-col span[title]")?.attr("title")
            ?.let { CHAPTER_DATE_FORMAT.tryParseDateTime(it, CHAPTER_ZONE) }
            ?: 0L
    }

    // ================================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("img.reader-img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    // ================================= Filters ===============================
    override fun getFilterList(data: JsonElement?) = jestfulFilters()

    // ================================ Utilities ==============================
    private fun fullResCover(url: String?): String? {
        val u = url?.let { COVER_PARAM.find(it)?.groupValues?.get(1) } ?: return url
        return runCatching { String(Base64.decode(u, Base64.URL_SAFE or Base64.NO_PADDING)) }.getOrElse { url }
    }

    private fun metaRow(document: Document, label: String): Element? = document.select(".manga-metadata-grid .meta-row").firstOrNull {
        it.selectFirst("b")?.text()?.trim()?.trimEnd(':')?.equals(label, ignoreCase = true) == true
    }

    private fun metaValue(document: Document, label: String): String? = metaRow(document, label)?.text()?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() }

    private fun altTitles(document: Document): List<String> = document.selectFirst(".manga-detail-alt")?.ownText()?.split(',', '、')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private fun ratingParts(document: Document): Pair<Double, Long?>? {
        val score = document.selectFirst(".h0rating")?.attr("data-score")?.toDoubleOrNull() ?: return null
        val votes = document.selectFirst(".rating-reviews-text")?.text()?.let { VOTES_REGEX.find(it)?.value?.replace(",", "")?.toLongOrNull() }
        return score to votes
    }

    companion object {
        private val PAGE_REGEX = Regex("""Page (\d+) of (\d+)""")
        private val VOTES_REGEX = Regex("""\d[\d,]*""")
        private val CHAPTER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        private val COVER_PARAM = Regex("""cover\.php\?u=([^&]+)""")
        private val CHAPTER_SUFFIX_REGEX = Regex("""-chapter-\d+(\.\d+)?\.html$""")
        private val CHAPTER_ZONE = ZoneId.of("Asia/Singapore")
    }
}
