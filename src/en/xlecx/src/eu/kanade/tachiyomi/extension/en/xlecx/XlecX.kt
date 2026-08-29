package eu.kanade.tachiyomi.extension.en.xlecx

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class XlecX : KeiSource() {

    // TODO: filters
    // TODO: description - text, other `subInfoLinks`s, JSON-LD,

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 403 && response.header("Content-Type")?.contains("text/html") == true) {
                val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
                val message = document.selectFirst(".message-info__content")
                if (message != null) {
                    val host = chain.request().url.host
                    val messageCleaned = message.text().replace(whitespaceRegex, " ")
                    if (messageCleaned.length > 200) {
                        throw Exception("Open WebView to see message from $host")
                    }
                    throw Exception("$host: $messageCleaned")
                }
            }
            response
        }
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val pageStr = if (page > 1) "page/$page/" else ""
        return parseMangasPage(client.get("$baseUrl/f/sort=news_read/order=desc/$pageStr"))
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pageStr = if (page > 1) "page/$page/" else ""
        return parseMangasPage(client.get("$baseUrl/f/sort=date/order=desc/$pageStr"))
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("do", "search")
            .addQueryParameter("subaction", "search")
            .addQueryParameter("search_start", page.toString())
            .addQueryParameter("full_search", "0")
            .addQueryParameter("story", query)
            .build()

        return parseMangasPage(client.get(url))
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("#dle-content > a.thumb")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst("#pagination > .pagination__pages > a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val img = element.selectFirst("img")!!
        title = img.attr("alt")
        thumbnail_url = img.absUrl("src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!postRegex.matches(url.encodedPath)) return null

        val manga = SManga.create().apply {
            this.url = url.encodedPath
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = url.encodedPath
            }
    }

    // Updates
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()

        val manga = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1")!!.text()
            artist = document.subInfoLinks("Artist:")
            author = document.subInfoLinks("Group:")
            genre = document.subInfoLinks("Tags:")
            // TODO: document.subInfoLinks("Parody:")
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
        }

        val script = document.selectFirst("script[type=application/ld+json]")?.data() ?: throw Exception("JSON-LD data not found")
        val dto = script.parseAs<JsonLdDto>()
        val book = dto.graph.firstOrNull() ?: return SMangaUpdate(manga, emptyList())

        val chapters = listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                date_upload = Instant.parseOrNull(book.dateModified ?: book.datePublished)?.toEpochMilliseconds() ?: 0L
                chapter_number = 1f
            },
        )

        return SMangaUpdate(manga, chapters)
    }

    private fun Element.subInfoLinks(label: String): String? = select(".page__subinfo-item > div:not([class]):contains($label) ~ a")
        ?.joinToString { it.text() }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()

        // 'Full size' tab
        document.select("#content-2 > .imagegall23 > img").mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // Plain
        document.select(".page__text a:has(img)").mapIndexed { index, element ->
            val imageUrl = element.absUrl("href")
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // 'Thumb' tab
        document.select("#content-1 > .imagegall23 > img").mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // JSON-LD, may contain junk pages
        val script = document.selectFirst("script[type=application/ld+json]")?.data() ?: throw Exception("JSON-LD data not found")
        val dto = script.parseAs<JsonLdDto>()
        val book = dto.graph.firstOrNull() ?: return emptyList()

        return book.image.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    companion object {
        private val whitespaceRegex = """\s+""".toRegex()
        private val postRegex = """/\d+-.*?\.html""".toRegex()
    }
}
