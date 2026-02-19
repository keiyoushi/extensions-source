package eu.kanade.tachiyomi.extension.en.rinkocomics

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RinkoComics :
    HttpSource(),
    ConfigurableSource {

    override val name = "Rinko Comics"

    override val baseUrl = "https://rinkocomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences by getPreferencesLazy()

    private var genresList: List<Genre> = emptyList()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(comicsUrl(page).build(), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseComicsPage(response)

    override fun latestUpdatesRequest(page: Int): Request = GET(
        comicsUrl(page)
            .addQueryParameter(SORT_PARAM, SortFilter.OPTIONS.first().second)
            .build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseComicsPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = comicsUrl(page)
            .addQueryParameter("post_type", "comic")

        if (query.isNotBlank()) {
            url.addQueryParameter("s", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.filter { it.state }
                        .forEach { url.addQueryParameter("genres[]", it.slug) }
                }

                is SortFilter -> {
                    filter.toQuery()?.let { url.addQueryParameter(SORT_PARAM, it) }
                }

                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseComicsPage(response)

    override fun mangaDetailsParse(response: Response): SManga = parseMangaDetails(response.asJsoup())

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = requireField(
            document.selectFirst(".comic-info-upper h1")?.text()
                ?: document.selectFirst("h1")?.text(),
            "title",
        )

        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")

        val authors = document.select(".comic-graph > span")
            .map { it.text() }
            .filter { it.isNotBlank() && it != "•" }
            .distinct()

        author = authors.firstOrNull()
        artist = authors.getOrNull(1)

        status = parseStatus(
            document.selectFirst(".comic-status span:last-child")?.text(),
        )

        genre = document.select(".comic-genres .genres .genre")
            .joinToString { it.text() }

        description = document.selectFirst(".comic-synopsis")?.text()?.trim()

        initialized = true
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED, false)

        val chapters = LinkedHashMap<String, SChapter>()
        fun addAll(items: List<SChapter>) {
            items.forEach { chapter ->
                if (!chapters.containsKey(chapter.url)) {
                    chapters[chapter.url] = chapter
                }
            }
        }

        addAll(parseChapterElements(document.select(CHAPTER_SELECTOR), hideLocked))

        val loadMoreBtn = document.selectFirst("#loadMoreChaptersBtn")
        val comicId = loadMoreBtn?.attr("data-comic-id").orEmpty()
        val nonce = extractNonce(document).orEmpty()
        var offset = loadMoreBtn?.attr("data-offset")?.toIntOrNull() ?: 0
        if (offset <= 0) {
            offset = chapters.size
        } else if (chapters.isNotEmpty() && offset > chapters.size) {
            offset = chapters.size
        }

        if (comicId.isNotBlank() && nonce.isNotBlank()) {
            while (true) {
                val items = fetchMoreChapters(comicId, offset, nonce, hideLocked)
                if (items.isEmpty()) break
                addAll(items)
                offset += CHAPTERS_PER_PAGE
            }
        }

        return chapters.values.toList()
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = document.select("img.chapter-image").mapIndexedNotNull { index, element ->
            val imageUrl = element.attr("abs:data-src").ifBlank { element.attr("abs:src") }.trim()
            if (imageUrl.isBlank()) return@mapIndexedNotNull null
            Page(index, imageUrl = imageUrl)
        }

        if (pages.isEmpty()) {
            throw Exception("Chapter is locked or unavailable.")
        }

        return pages
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.contains(LOCK_SUFFIX)) {
            throw Exception("This chapter is locked. Use WebView to purchase it.")
        }
        return super.pageListRequest(chapter)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        if (genresList.isNotEmpty()) {
            filters += GenreFilter(genresList)
        } else {
            filters += Filter.Header("Press reset to load genres")
        }

        filters += SortFilter()

        return FilterList(filters)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED
            title = "Hide paid chapters"
            summary = "Hide locked/paid chapters from the list."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun comicsUrl(page: Int): HttpUrl.Builder {
        val url = if (page <= 1) {
            "$baseUrl/comic/"
        } else {
            "$baseUrl/comic/page/$page/"
        }
        return url.toHttpUrl().newBuilder()
    }

    private fun parseComicsPage(response: Response): MangasPage {
        val document = response.asJsoup()

        if (genresList.isEmpty()) {
            genresList = parseGenres(document)
        }

        val entries = document.select("article.ac-card").mapNotNull { card ->
            val url = card.selectFirst(".ac-title a")?.attr("abs:href")?.trim().orEmpty()
            if (url.isBlank()) return@mapNotNull null
            val title = requireField(card.selectFirst(".ac-title a")?.text(), "title")

            SManga.create().apply {
                setUrlWithoutDomain(url)
                this.title = title
                thumbnail_url = card.selectFirst(".ac-thumb img")?.let { imageFromElement(it) }
            }
        }

        val hasNextPage = document.selectFirst(".ac-pagination a.next") != null

        return MangasPage(entries, hasNextPage)
    }

    private fun parseGenres(document: Document): List<Genre> = document.select(".ac-filter-group.ac-genre input[name='genres[]']")
        .mapNotNull { input ->
            val slug = input.attr("value").trim()
            val name = input.parent()?.selectFirst(".ac-option-text")?.text()?.trim().orEmpty()
            if (slug.isBlank() || name.isBlank()) null else Genre(name, slug)
        }

    private fun parseChapterElements(elements: List<Element>, hideLocked: Boolean): List<SChapter> {
        return elements.mapNotNull { element ->
            val permalink = element.attr("data-permalink").trim()
            val href = element.selectFirst("a")?.attr("abs:href").orEmpty()
            val url = permalink.ifBlank { href }
            if (url.isBlank()) return@mapNotNull null

            val name = element.selectFirst(".chapter-number")?.text()
                ?: element.attr("data-title")
            val dateText = element.selectFirst(".chapter-date")?.text()
            val locked = isLocked(element)

            if (locked && hideLocked) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(url)
                this.name = name?.trim().orEmpty()
                date_upload = parseDate(dateText)

                if (locked) {
                    this.name = "$LOCK_PREFIX${this.name}"
                    this.url += LOCK_SUFFIX
                }
            }
        }
    }

    private fun isLocked(element: Element): Boolean {
        val reason = element.attr("data-reason").lowercase(Locale.ROOT)
        if (reason.isNotBlank() && reason != "free") return true
        if (element.hasClass("locked-chapter")) return true

        val href = element.selectFirst("a")?.attr("href").orEmpty()
        if (href.isBlank() || href == "#") return true

        return element.selectFirst(".chapter_price") != null
    }

    private fun fetchMoreChapters(
        comicId: String,
        offset: Int,
        nonce: String,
        hideLocked: Boolean,
    ): List<SChapter> {
        val formBody = FormBody.Builder()
            .add("action", "load_more_chapters")
            .add("nonce", nonce)
            .add("comic_id", comicId)
            .add("offset", offset.toString())
            .build()

        val xhrHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
        val request = POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
        val result = client.newCall(request).execute().parseAs<AjaxResponse>()
        if (!result.success) return emptyList()

        val html = result.data?.html.orEmpty()
        if (html.isBlank()) return emptyList()

        val doc = Jsoup.parseBodyFragment(html)
        return parseChapterElements(doc.select(CHAPTER_SELECTOR), hideLocked)
    }

    private fun extractNonce(document: Document): String? {
        val match = NONCE_REGEX.find(document.html()) ?: return null
        return match.groupValues[1]
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase(Locale.ROOT)) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.UNKNOWN
        "cancelled", "canceled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseDate(date: String?): Long {
        date ?: return 0L
        return dateFormats.firstNotNullOfOrNull { formatter ->
            formatter.tryParse(date).takeIf { it != 0L }
        } ?: 0L
    }

    private fun imageFromElement(element: Element): String? {
        val imageUrl = when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            else -> element.attr("abs:src")
        }
        return imageUrl.trim().ifEmpty { null }
    }

    private fun requireField(value: String?, label: String): String {
        val trimmed = value?.trim()
        if (trimmed.isNullOrBlank()) {
            throw Exception("Missing $label")
        }
        return trimmed
    }

    class Genre(name: String, val slug: String) : Filter.CheckBox(name)

    class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    class SortFilter : Filter.Select<String>("Sort", OPTIONS.map { it.first }.toTypedArray()) {
        fun toQuery(): String? = OPTIONS.getOrNull(state)?.second

        companion object {
            val OPTIONS = listOf(
                Pair("Newest First", "newest"),
                Pair("Oldest First", "oldest"),
                Pair("A-Z", "az"),
                Pair("Z-A", "za"),
            )
        }
    }

    private val dateFormats = listOf(
        SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH),
        SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH),
    )

    @Serializable
    private data class AjaxResponse(
        val success: Boolean = false,
        val data: AjaxData? = null,
    )

    @Serializable
    private data class AjaxData(
        val html: String? = null,
    )

    companion object {
        private const val PREF_HIDE_LOCKED = "hide_paid_chapters"
        private const val SORT_PARAM = "sort"
        private const val LOCK_PREFIX = "🔒 "
        private const val LOCK_SUFFIX = "#lock"
        private const val CHAPTER_SELECTOR = "li.chapter"
        private const val CHAPTERS_PER_PAGE = 10
        private val NONCE_REGEX = Regex(
            """comicworld_ajax\s*=\s*\{[^}]*"nonce"\s*:\s*"([^"]+)"""",
        )
    }
}
