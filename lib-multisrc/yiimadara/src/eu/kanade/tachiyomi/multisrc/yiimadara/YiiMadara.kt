package eu.kanade.tachiyomi.multisrc.yiimadara

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

// Yii2 sites that copy Madara's markup
abstract class YiiMadara : KeiSource() {

    protected open val mangaPath = "manga"

    // Catalog route sorted with ?orderby=, null when the site has none
    protected open val directoryPath: String? = "manga"

    // Paginate as /<path>/page/<n> instead of /<path>/<n>
    protected open val pageSegment = false

    protected open val trailingSlash = false

    protected open val searchParam = "q"

    protected open val completedPath: String? = "completed"

    protected open val rawPath: String? = null

    // Untranslated raws are mixed into every listing
    protected open val rawEntries: RawEntries
        get() = when (lang) {
            "en" -> RawEntries.HIDE
            "ko" -> RawEntries.ONLY
            else -> RawEntries.SHOW
        }

    // No orderby is the latest updates order, orderby=latest is newest series
    protected open val sortOptions = listOf(
        "Default" to "",
        "Trending" to "trending",
        "Newest" to "latest",
        "Rating" to "rating",
        "A-Z" to "alphabet",
    )

    override val supportsFilterFetching = true

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = directoryPage(page, "trending")

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = directoryPage(page, null)

    private suspend fun directoryPage(page: Int, order: String?): MangasPage {
        val path = directoryPath!!
        val url = listUrl(path, page).apply {
            if (order != null) addQueryParameter("orderby", order)
        }.build()
        return mangaListParse(client.get(url).asJsoup(), rawListing = path == rawPath)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("search")
                if (trailingSlash) addPathSegment("")
                // Search fails with HTTP 500 on ASCII apostrophes
                addQueryParameter(searchParam, query.replace('\'', '’'))
                addQueryParameter("page", page.toString())
            }.build()
            return mangaListParse(client.get(url).asJsoup())
        }

        val path = filters.firstInstanceOrNull<GenreFilter>()?.value?.takeIf(String::isNotEmpty)
            ?: filters.firstInstanceOrNull<CategoryFilter>()?.value?.takeIf(String::isNotEmpty)
            ?: directoryPath
            ?: return getLatestUpdates(page)
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value
        val url = listUrl(path, page).apply {
            if (!sort.isNullOrEmpty()) addQueryParameter("orderby", sort)
        }.build()
        return mangaListParse(client.get(url).asJsoup(), rawListing = path == rawPath)
    }

    protected open fun listUrl(path: String, page: Int): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegments(path)
        if (pageSegment) addPathSegment("page")
        addPathSegment(page.toString())
        if (trailingSlash) addPathSegment("")
    }

    protected fun mangaListParse(document: Document, rawListing: Boolean = false): MangasPage {
        val mangas = document.select("div.page-item, div.page-item-detail, div.home-item, div.manga-item").map { element ->
            SManga.create().apply {
                val link = element.selectFirst("h3 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst("img")?.imgUrl()
            }
        }.filterRaw(rawListing)
        return MangasPage(mangas, document.selectFirst("#blog-pager li.next a") != null)
    }

    private fun List<SManga>.filterRaw(rawListing: Boolean = false) = filter { manga ->
        when (rawEntries) {
            RawEntries.SHOW -> true
            RawEntries.HIDE -> rawListing || !manga.isRaw()
            RawEntries.ONLY -> rawListing || manga.isRaw()
        }
    }

    private fun SManga.isRaw() = url.trimEnd('/').endsWith("-raw") || title.endsWith(" raw", ignoreCase = true)

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != mangaPath) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val manga = SManga.create().apply {
            this.url = "/$mangaPath/$slug" + if (trailingSlash) "/" else ""
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document).apply { url = manga.url }, chapterListParse(document))
    }

    protected open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val heading = document.selectFirst(".post-title h1")!!
        // Some sites put an "18+" badge inside the heading
        title = heading.ownTextOrNull() ?: heading.text()
        thumbnail_url = document.selectFirst(".summary_image img")?.imgUrl()
        author = document.info("Author")?.names()
        artist = document.info("Artist")?.names()
        genre = listOfNotNull(document.info("Genre")?.names(), document.info("Type")?.value())
            .joinToString()
            .ifEmpty { null }
        status = when (document.info("Status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        val synopsis = document.selectFirst(".dsct, .ss-manga, .post-content_item .entry-content")?.let { block ->
            block.select("p").ifEmpty { listOf(block) }
                .mapNotNull { it.textOrNull() }
                .joinToString("\n\n")
                .ifEmpty { null }
        }
        val altNames = document.info("Alternative")?.value()
        description = listOfNotNull(synopsis, altNames?.let { "Alternative names: $it" })
            .joinToString("\n\n")
            .ifEmpty { null }
    }

    // Rows are either a .summary-heading/.summary-content pair or a <b> label followed by the value,
    // some sites instead put Status/Type under the cover as `.minfo` with the value in <i>
    private fun Document.info(label: String): Elements? {
        val item = select(".post-content_item").firstOrNull {
            it.selectFirst("> .summary-heading, > b")?.text()?.startsWith(label, ignoreCase = true) == true
        } ?: return select(".minfo").firstOrNull { it.ownText().startsWith(label, ignoreCase = true) }?.select("> i")
        return item.select("> .summary-content").ifEmpty { item.select("> :not(b)") }
    }

    private fun Elements.value(): String? = text().takeUnless { it.isEmpty() || it == "Updating" || it == "N/A" }

    private fun Elements.names(): String? {
        val links = select("a")
        val names = if (links.isEmpty()) listOfNotNull(value()) else links.eachText()
        return names.filter { it.isNotEmpty() && it != "Updating" }.joinToString().ifEmpty { null }
    }

    // ============================== Related ===============================

    override val supportsRelatedMangas = true

    // Served from the HTTP cache right after the details request
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select(".related-manga a:has(img), .relations-box a:has(img), .relation-content a:has(img)")
        .map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.attr("title").ifEmpty { link.selectFirst("img")!!.attr("alt") }
                thumbnail_url = link.selectFirst("img")?.imgUrl()
            }
        }
        .distinctBy { it.url }
        .filterRaw()

    // ============================== Chapters ==============================

    private fun chapterListParse(document: Document): List<SChapter> = document.select("ul.row-content-chapter li").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst("a")!!
            setUrlWithoutDomain(link.absUrl("href"))
            name = link.text()
            date_upload = dateFormat.tryParseDate(element.selectFirst(".chapter-time")?.text())
        }
    }

    // Sources previously on the Madara theme stored only the chapter slug, with the manga path in memo
    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.startsWith("/")) return super.getChapterUrl(chapter)
        val mangaPath = chapter.memo["mangaPath"]?.stringOrNull ?: error("Refresh the chapter list")
        return "$baseUrl${mangaPath.trimEnd('/')}/${chapter.url}"
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select(".read-content img")
        .mapIndexed { index, element -> Page(index, imageUrl = element.imgUrl()) }

    protected fun Element.imgUrl(): String = absUrl("data-src").ifEmpty { absUrl("src") }

    // =============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement = client.get(baseUrl).asJsoup()
        .select("a[href*=genre/]")
        .map { it.text() to it.absUrl("href").toHttpUrl().encodedPath.trim('/') }
        .distinctBy { it.second }
        .sortedBy { it.first }
        .toJsonElement()

    protected open fun genres(data: JsonElement?): List<Pair<String, String>> = data?.parseAs() ?: emptyList()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = genres(data)
        val categories = listOfNotNull(completedPath?.let { "Completed" to it }, rawPath?.let { "Raw" to it })
        return FilterList(
            buildList {
                add(Filter.Header("Ignored when using text search; Genre takes priority over Category"))
                if (genres.isNotEmpty()) add(GenreFilter(listOf("All" to "") + genres))
                if (categories.isNotEmpty()) add(CategoryFilter(listOf("All" to "") + categories))
                add(SortFilter(sortOptions))
            },
        )
    }

    enum class RawEntries { SHOW, HIDE, ONLY }

    companion object {
        // "09 Oct 2026" or "09 Oct 26"
        private val dateFormat = DateTimeFormatterBuilder()
            .appendPattern("dd MMM [yyyy][yy]")
            .toFormatter(Locale.ENGLISH)
    }
}
