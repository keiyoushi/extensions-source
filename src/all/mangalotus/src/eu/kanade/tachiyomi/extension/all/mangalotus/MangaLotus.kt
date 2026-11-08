package eu.kanade.tachiyomi.extension.all.mangalotus

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

// Yaoi Manga Online's posts under other titles and addresses, with their pages loaded from yaoimangaonline.com
@Source
abstract class MangaLotus : KeiSource() {

    override val supportsLatest = false

    override val supportsRelatedMangas get() = true

    override val supportsFilterFetching get() = true

    private val languageTag get() = LANGUAGE_TAGS[lang]

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            languageTag?.let { addPathSegments("tag/$it/") }
            if (page > 1) addPathSegments("page/$page/")
        }.build()

        return parseMangasPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".post:not(.sticky)$MANGA_POST > div > a")
            .map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".herald-pagination > .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        title = element.attrOrNull("title")!!
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.absUrl("src")?.replace(THUMBNAIL_SIZE, "")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("page/$page/")
            addQueryParameter("s", query)
            filters.firstInstanceOrNull<CategoryFilter>()?.selected?.let { addQueryParameter("cat", it) }
            languageTag?.let { addQueryParameter("tag", it) }
        }.build()

        return parseMangasPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        // Posts are /slug/, and the pages of a long one /slug/N/
        val slug = when {
            segments.size == 1 -> segments[0]
            segments.size == 2 && segments[1].toIntOrNull() != null -> segments[0]
            else -> return null
        }
        val mangaUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).addPathSegment("").build()
        val document = client.get(mangaUrl, Fetch.DOCUMENT, ensureSuccess = false).asJsoup()
        if (document.selectFirst("article.herald-single") == null) return null
        return mangaDetailsParse(document).apply {
            this.url = "/$slug/"
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        description = MarkdownDescription.Builder()
            .description(document.select(".entry-content > p:not(:has(img))").joinToString("\n\n") { it.wholeText() })
            .build()
        // The first category is the post's own, the next ones belong to the related posts
        val categories = document.selectFirst(".meta-category")?.select("a").orEmpty()
        genre = (categories + document.select(".meta-tags > a"))
            .joinToString { it.text() }
            .ifEmpty { null }
        status = if (document.selectFirst("article.herald-single.tag-completed-yaoi-manga") != null) SManga.COMPLETED else SManga.UNKNOWN
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("#related .post$MANGA_POST > div > a")
        .map(::mangaFromElement)

    private fun chapterListParse(document: Document): List<SChapter> {
        val chapters = document.select(".mpp-toc a").map { element ->
            SChapter.create().apply {
                name = element.ownText()
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        val published = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
        return chapters.ifEmpty {
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = document.location().toHttpUrl().encodedPath
                },
            )
        }.reversed().also { it.first().date_upload = published }
    }

    // Older posts link their pages over http, which yaoimangaonline.com redirects to https: the extra requests get 429s
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".entry-content img")
        .mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src").replaceFirst(HTTP, "https://")) }

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        .select("select#cat option:not([value=-1])")
        .filter { it.attr("value") !in NON_MANGA_CATEGORIES }
        .map { Option(it.text().substringBeforeLast("(").trim(), it.attr("value")) }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<Option>>() ?: return FilterList()
        return FilterList(CategoryFilter(categories))
    }

    companion object {
        // Yaoi Anime
        private val NON_MANGA_CATEGORIES = setOf("4")
        private const val MANGA_POST = ":not(.category-yaoi-anime-online)"

        private val THUMBNAIL_SIZE = Regex("""-\d+x\d+(?=\.\w+$)""")
        private val HTTP = Regex("^http://")

        private val LANGUAGE_TAGS = mapOf(
            "en" to "english",
            "ja" to "japanese",
            "zh" to "chinese",
            "ko" to "korean",
        )
    }
}
