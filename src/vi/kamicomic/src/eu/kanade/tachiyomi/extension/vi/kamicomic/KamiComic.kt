package eu.kanade.tachiyomi.extension.vi.kamicomic

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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.Calendar

@Source
abstract class KamiComic : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaListPage(client.get("$baseUrl/bang-xep-hang-truyen/page/$page/").asJsoup())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaListPage(client.get("$baseUrl/moi-cap-nhat/page/$page/").asJsoup())

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = (if (page == 1) baseUrl else "$baseUrl/page/$page/").toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            return parseMangaListPage(client.get(url).asJsoup())
        }

        val selectedGenre = filters.firstInstanceOrNull<GenreFilter>()?.selectedSlug()
        if (selectedGenre != null) {
            return parseMangaListPage(client.get("$baseUrl/the-loai/$selectedGenre/page/$page/").asJsoup())
        }

        return getLatestUpdates(page)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/the-loai/").asJsoup()
        .select("main a[href*=/the-loai/]:has(h2)")
        .mapNotNull { link ->
            val genreUrl = link.absUrl("href").toHttpUrl()
            val genreIndex = genreUrl.pathSegments.indexOf("the-loai")
            val slug = genreUrl.pathSegments.getOrNull(genreIndex + 1) ?: return@mapNotNull null
            val name = link.selectFirst("h2")?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            GenreOption(name, slug)
        }
        .distinctBy { it.slug }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // ============================== Parsing ===============================

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaList = document.select("a.uk-link-heading[href*=/truyen/]").mapNotNull { link ->
            val mangaUrl = link.absUrl("href")
            if (mangaUrl.isNovelUrl()) return@mapNotNull null

            val panel = link.closest(".uk-panel") ?: link.parent()
            SManga.create().apply {
                setUrlWithoutDomain(mangaUrl)
                title = link.text()
                thumbnail_url = panel?.selectFirst("img")?.absUrl("src")
                    ?.removeThumbnailSizeSuffix()
            }
        }

        val hasNextPage = document.select("ul.uk-pagination a[aria-label='Trang sau'][href]")
            .any { link -> link.attr("href") != "#" && link.parent()?.hasClass("uk-disabled") != true }

        return MangasPage(mangaList, hasNextPage)
    }

    private fun String.isNovelUrl(): Boolean = toHttpUrl().pathSegments
        .take(2)
        .joinToString("/")
        .startsWith("truyen/novel", ignoreCase = true)

    private fun String.removeThumbnailSizeSuffix(): String = replace(thumbSizeRegex) { it.groupValues[1] }

    // =============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "truyen") return null

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/truyen/$slug/")
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = if (fetchDetails) async { loadMangaDetails(manga) } else null
        val chapterList = if (fetchChapters) async { loadChapterList(manga) } else null

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    private suspend fun loadMangaDetails(manga: SManga): SManga {
        val slug = manga.url
            .removeSuffix("/")
            .substringAfterLast("/")
        val url = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
            .build()
        val mangaList = client.get(url).parseAs<List<WpManga>>()
        val wpManga = mangaList.first()

        return SManga.create().apply {
            setUrlWithoutDomain(manga.url)
            title = Jsoup.parseBodyFragment(wpManga.title.rendered).text()

            description = wpManga.content?.rendered?.let { html ->
                Jsoup.parseBodyFragment(html).text()
            }

            val terms = wpManga.embedded?.terms.orEmpty().flatten()

            genre = terms
                .filter { it.taxonomy == "genre" }
                .mapNotNull { it.name }
                ?.joinToString()
                ?.ifEmpty { null }

            author = terms
                .filter { it.taxonomy == "author_tax" }
                .mapNotNull { it.name }
                ?.joinToString()
                ?.ifEmpty { null }

            thumbnail_url = wpManga.embedded?.featuredMedia
                ?.firstOrNull()?.sourceUrl

            status = SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private suspend fun loadChapterList(manga: SManga): List<SChapter> = coroutineScope {
        val mangaUrl = "$baseUrl${manga.url}".removeSuffix("/")
        val document = client.get(mangaUrl).asJsoup()
        val chapters = parseChapters(document)

        // Handle multi-page chapter lists
        val paginationLinks = document.select("ul.uk-pagination li a[href*=/chuong/page/]")
        val maxPage = paginationLinks.mapNotNull { link ->
            pageNumberRegex.find(link.absUrl("href"))
                ?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1

        val remainingChapters = (2..maxPage)
            .map { page ->
                async {
                    val pageUrl = "$mangaUrl/chuong/page/$page/"
                    parseChapters(client.get(pageUrl).asJsoup())
                }
            }
            .awaitAll()
            .flatten()

        chapters + remainingChapters
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".chapter-list a.uk-link-toggle").map { element ->
        val rawName = element.selectFirst("h3")?.text()
            ?: element.text()
        val chapterName = chapterNameRegex.find(rawName)?.value ?: rawName
        val isLocked = element.selectFirst("[uk-icon=\"icon: lock\"], .uk-text-danger[uk-icon]") != null ||
            element.parent()?.selectFirst("[uk-icon=\"icon: lock\"], .uk-text-danger[uk-icon]") != null

        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = if (isLocked) "🔒 $chapterName" else chapterName
            date_upload = element.selectFirst("time")?.text()
                .parseRelativeDate()
        }
    }

    private fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val calendar = Calendar.getInstance()
        val number = numberRegex.find(this)?.value?.toIntOrNull() ?: return 0L

        when {
            contains("giây") -> calendar.add(Calendar.SECOND, -number)
            contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        val relatedSection = document.select("h2")
            .firstOrNull { it.text() == "Truyện liên quan" }
            ?.parent()
            ?: return emptyList()

        return relatedSection.select(".manga-item-slider a.uk-link-toggle[href*=/truyen/]").mapNotNull { link ->
            val title = link.selectFirst("h3")?.text()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = link.selectFirst("img")?.absUrl("src")
                    ?.removeThumbnailSizeSuffix()
            }
        }.distinctBy { it.url }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()

        if (document.selectFirst("#chapter-content .lock-card, #chapter-content #unlock-chapter, #chapter-content #xu-lock") != null) {
            return emptyList()
        }

        return document.select("#chapter-content img").mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-original-src")
                .ifEmpty { element.absUrl("src") }
                .takeIf { it.isNotEmpty() && !it.startsWith("data:") }
                ?: return@mapIndexedNotNull null

            Page(index, imageUrl = imageUrl)
        }
    }

    private val numberRegex = Regex("""\d+""")
    private val pageNumberRegex = Regex("""/page/(\d+)/""")
    private val thumbSizeRegex = Regex("""-150x150(\.\w+)$""")
    private val chapterNameRegex = Regex("""Chương \d+.*""")
}
