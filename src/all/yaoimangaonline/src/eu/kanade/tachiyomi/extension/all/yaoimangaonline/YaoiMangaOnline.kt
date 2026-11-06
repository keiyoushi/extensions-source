package eu.kanade.tachiyomi.extension.all.yaoimangaonline

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

@Source
abstract class YaoiMangaOnline : KeiSource() {

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
        val tags = buildList {
            languageTag?.let(::add)
            filters.firstInstanceOrNull<TagFilter>()?.state?.filter { it.state }?.forEach { add(it.value) }
        }
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            // Root /?s= gets a Cloudflare challenge, /page/1/?s= does not
            addPathSegments("page/$page/")
            addQueryParameter("s", query)
            filters.firstInstanceOrNull<CategoryFilter>()?.selected?.let { addQueryParameter("cat", it) }
            if (tags.isNotEmpty()) addQueryParameter("tag", tags.joinToString("+"))
        }.build()

        return parseMangasPage(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        // Posts are /slug/ and their chapters /slug/N/
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
        title = document.selectFirst("h1.entry-title")!!.text().substringBeforeLast(" by ")
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        description = document
            .select(".entry-content > p:not(:has(img)):not(:contains(You need to login))")
            .joinToString("\n\n") { it.wholeText() }
            .ifEmpty { null }
        genre = document.select(".meta-category a:not([href*=/mangaka/]):not([href~=/yaoi-webtoons/.+]), .meta-tags > a")
            .joinToString { it.text() }
            .ifEmpty { null }
        author = document.select(".meta-category a[href*=/mangaka/]").joinToString { it.text() }.ifEmpty { null }
        val article = document.selectFirst("article.herald-single")!!
        status = when {
            article.hasClass("category-ongoing") -> SManga.ONGOING
            article.hasClass("category-completed") || COMPLETED_TAGS.any(article::hasClass) -> SManga.COMPLETED
            article.hasClass("category-hiatus") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select(".entry-content img")
        .mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src")) }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        val categories = document.select("select#cat option:not([value=-1])")
            .filter { it.attr("value") !in NON_MANGA_CATEGORIES }
            .map { Option(it.text().substringBeforeLast("(").trim(), it.attr("value")) }
        val tags = document.select(".tagcloud a")
            .map { Option(it.text(), it.absUrl("href").toHttpUrl().pathSegments[1]) }
        return FilterData(categories, tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        val tags = filterData.tags.filter { languageTag == null || it.value !in LANGUAGE_TAGS.values }
        return FilterList(CategoryFilter(filterData.categories), TagFilter(tags))
    }

    companion object {
        // Gay Movies, Yaoi Anime
        private val NON_MANGA_CATEGORIES = setOf("3017", "2009")
        private const val MANGA_POST = ":not(.category-gay-movies):not(.category-yaoi-anime)"

        private val COMPLETED_TAGS = listOf("tag-completed-yaoi-manga", "tag-completed-yaoi-manhwa")
        private val THUMBNAIL_SIZE = Regex("""-\d+x\d+(?=\.\w+$)""")

        private val LANGUAGE_TAGS = mapOf(
            "en" to "english-yaoi",
            "ja" to "japanese-yaoi",
            "zh" to "chinese-yaoi",
            "ko" to "korean",
            "es" to "spanish",
        )
    }
}
