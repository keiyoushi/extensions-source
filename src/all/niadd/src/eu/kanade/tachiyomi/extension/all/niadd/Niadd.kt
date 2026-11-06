package eu.kanade.tachiyomi.extension.all.niadd

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Niadd : KeiSource() {

    // Causes pageList 302
    override fun Headers.Builder.configureHeaders() = removeAll("Referer")

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/list/Hot-Manga.html", Fetch.DOCUMENT).asJsoup())

    private fun popularMangaSelector() = "div.manga-item"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.manga-name")!!.text()
        val rawUrl = element.selectFirst("a")!!.absUrl("href")
        setUrlWithoutDomain(rawUrl)
        element.selectFirst("div.manga-img img")?.attr("abs:src")?.also { thumbnail_url = it }
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        return MangasPage(mangas, false)
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size < 2) return null

        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .build()
        return parseMangaList(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/list/New-Update.html", Fetch.DOCUMENT).asJsoup())

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) fetchDetails(manga) else manga }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }

        SMangaUpdate(details.await(), chapterList.await())
    }

    // Details
    private suspend fun fetchDetails(manga: SManga): SManga = SManga.create().apply {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val infoElement = document.select("div.bookside-general, div.detail-general")

        url = manga.url
        title = document.selectFirst("h1, .book-headline-name")!!.text()
        author = infoElement.select(".detail-general-cell:contains(Autor) span, [itemprop=author] span").text()
            .replace("Autor (es):", "", ignoreCase = true)
        artist = infoElement.select(".detail-general-cell:contains(Artista) span").text()
            .replace("Artista:", "", ignoreCase = true)
        genre = document.select("[itemprop=genre]").eachText().joinToString()

        val yearKeywords = listOf(
            "Released:",
            "Lanzado:",
            "Rilasciato:",
            "Выпущенный:",
            "Liberado:",
            "Freigegeben:",
        )

        val yearRaw = infoElement.select(".detail-general-cell").firstOrNull { cell ->
            yearKeywords.any { cell.text().contains(it, ignoreCase = true) }
        }?.selectFirst("span")?.text().orEmpty()

        val yearClean = yearRaw
            .let { text ->
                yearKeywords.fold(text) { acc, keyword -> acc.replace(keyword, "", ignoreCase = true) }
            }

        val synopsisKeywords = listOf(
            "Synopsis",
            "Sinopsis",
            "Sinossi",
            "конспект",
            "Sinopse",
            "Zusammenfassung",
        )

        val synopsisText = run {
            val titles = document.select(".detail-cate-title")
            for (title in titles) {
                val titleText = title.text()
                if (synopsisKeywords.any { keyword -> titleText.contains(keyword, ignoreCase = true) }) {
                    val nextSection = title.nextElementSibling()
                    if (nextSection != null && nextSection.hasClass("detail-section")) {
                        if (!nextSection.select("a[itemprop=genre]").any()) {
                            return@run nextSection.text()
                        }
                    }
                }
            }
            ""
        }

        description = buildString {
            if (yearClean.isNotEmpty()) append("Ano: $yearClean\n\n")
            if (synopsisText.isNotEmpty()) append(synopsisText)
        }

        document.selectFirst("div.detail-img img, div.bookside-img img")?.attr("abs:src").also { thumbnail_url = it }
        status = SManga.ONGOING
    }

    // Chapters
    private val chapterListSelector = "ul.chapter-list a.hover-underline"

    private fun parseDate(dateString: String): Long {
        if (dateString.contains("atrás", ignoreCase = true) ||
            dateString.contains("ago", ignoreCase = true)
        ) {
            return 0L
        }

        return dateFormat.tryParseDate(dateString)
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val chaptersUrl = baseUrl + manga.url.removeSuffix(".html") + "/chapters.html"
        // Sent as built: to requests that look like a browser the site serves /go/rds/ ad redirects instead of chapter links
        val document = client.get(chaptersUrl, Fetch.NONE).asJsoup()
        document.selectFirst("ul.chapter-list")!!

        return document.select(chapterListSelector).map { chapterFromElement(it) }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val rawUrl = element.attr("abs:href")
        setUrlWithoutDomain(rawUrl)

        name = element.selectFirst("span.chp-title, span.chapter-name, span.name")?.text()
            ?.takeIf(String::isNotEmpty)
            ?: element.text()

        element.selectFirst("span.chp-time, span.chapter-time, span.time")?.text()
            ?.also { date_upload = parseDate(it) }

        chapter_number = CHAPTER_NUMBER_REGEX.find(name)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = getChapterUrl(chapter).toHttpUrl().pathSegments[2]
        val url = readerJumpUrl().newBuilder().setQueryParameter("cid", chapterId).build()
        val html = try {
            client.get(url, Fetch.DOCUMENT.from(getChapterUrl(chapter))).body.string()
        } catch (e: Exception) {
            readerJumpUrl = null
            throw e
        }
        val imageList = ALL_IMGS_URL_REGEX.find(html)?.groupValues?.get(1) ?: run {
            readerJumpUrl = null
            throw Exception("Chapter pages not found on the site")
        }

        return IMAGE_URL_REGEX.findAll(imageList).mapIndexed { i, match ->
            Page(i, imageUrl = match.groupValues[1])
        }.toList()
    }

    private val readerJumpMutex = Mutex()
    private var readerJumpUrl: HttpUrl? = null

    // The site's /chapter/ links redirect to a dead host. Its chapter lists link each chapter through /go/rds/
    // to a reader jump url that only differs in cid, and the reader page needs a niadd Referer
    private suspend fun readerJumpUrl(): HttpUrl = readerJumpMutex.withLock {
        readerJumpUrl ?: run {
            val mangaUrl = client.get("$baseUrl/list/New-Update.html", Fetch.DOCUMENT).asJsoup()
                .selectFirst("${popularMangaSelector()} a")!!.absUrl("href")
            val redirectUrl = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
                .selectFirst("a[href*=/go/rds/]")
                ?.absUrl("href")
                ?: throw Exception("The site has no reader for this language")
            val script = client.get(redirectUrl, Fetch.DOCUMENT.from(mangaUrl)).body.string()
            JUMP_URL_REGEX.find(script)!!.groupValues[1].toHttpUrl()
        }.also { readerJumpUrl = it }
    }

    companion object {
        private val JUMP_URL_REGEX = Regex("""document\.location\s*=\s*'([^']+)'""")
        private val ALL_IMGS_URL_REGEX = Regex("""all_imgs_url\s*:\s*\[([\s\S]*?)\]""")
        private val IMAGE_URL_REGEX = Regex(""""(https?://[^"]+)"""")
        private val CHAPTER_NUMBER_REGEX = Regex("""Capítulo\s+(\d+(\.\d+)?)""")
        private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
    }
}
