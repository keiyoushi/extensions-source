package eu.kanade.tachiyomi.extension.all.wporncomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class WPornComics : KeiSource() {

    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int) = fetchList(listUrl("", page, SORT_VIEWS))

    override suspend fun getLatestUpdates(page: Int) = fetchList(listUrl("", page, SORT_DATE))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().state
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val tag = filters.firstInstance<TagFilter>().state.trim()
        val studio = filters.firstInstance<StudioFilter>().state.trim()

        val url = when {
            query.isNotBlank() -> listUrl("search", page, sort) { addQueryParameter("q", query.trim()) }
            category != null -> listUrl("categories", page, sort) { addQueryParameter("categori", category) }
            tag.isNotEmpty() -> listUrl("tag_page", page, sort) { addQueryParameter("t", tag) }
            studio.isNotEmpty() -> listUrl("studios", page, sort) { addQueryParameter("categori", studio) }
            filters.firstInstance<ListFilter>().best -> listUrl("zone", page, sort) { addQueryParameter("go", "best") }
            else -> listUrl("", page, sort)
        }
        return fetchList(url)
    }

    private fun listUrl(path: String, page: Int, sort: Int, block: HttpUrl.Builder.() -> Unit = {}) = baseUrl.toHttpUrl().newBuilder().apply {
        if (path.isNotEmpty()) addPathSegment(path).addPathSegment("")
        block()
        sortKey(path, sort)?.let { addQueryParameter("sort", it) }
        addQueryParameter("page", page.toString())
    }.build()

    // Keys from each list's own sort menu, which differ between the language sites and between lists.
    // Without a key, lists are sorted by date
    private fun sortKey(path: String, sort: Int): String? {
        val (views, likes) = when {
            path == "search" || path == "studios" -> "prosmotr" to if (lang == "de") "likely" else "like"
            lang == "de" && path.isEmpty() -> "prosmotr" to "likely"
            lang == "de" -> "views" to "likelyd_it"
            lang == "es" -> "vistas" to "liked_it"
            else -> "views" to "liked_it"
        }
        return when (sort) {
            SORT_VIEWS -> views
            SORT_LIKES -> likes
            else -> null
        }
    }

    private suspend fun fetchList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Ads take some of the grid's items
        val mangas = document.select("#ul-comix-set > li:has(> h2.block-name-comix-set > a)").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("a.pstr_next") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("> h2.block-name-comix-set > a")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.text().cleanTitle()
        thumbnail_url = selectFirst(".block-images-comix-set img")?.absUrl("data-src")
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in COMIC_FOLDERS) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val link = document.selectFirst("h1.block-name-comix > a") ?: return null
        return document.toDetailsSManga().apply {
            setUrlWithoutDomain(link.absUrl("href"))
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
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(
                document.selectFirst("ul.comix-info > li:contains(Date:) > i")?.text(),
            )
        }
        return SMangaUpdate(document.toDetailsSManga().apply { url = manga.url }, listOf(chapter))
    }

    private fun Document.toDetailsSManga() = SManga.create().apply {
        val heading = selectFirst("h1.block-name-comix")!!.text()
        val info = select("#foot-block-comix-category")
        title = heading.cleanTitle()
        thumbnail_url = selectFirst(".block-images-comix img")?.absUrl("data-src")
        author = info.select("a[href*=/studios/]").joinToString { it.text() }.ifEmpty { null }
        genre = (info.select("a[href*=/categories/]") + info.select("a[href*=/tag_page/]"))
            .map { it.text() }
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        // The summary starts with the full title, sometimes after an "updated" note
        description = MarkdownDescription.Builder()
            .description(selectFirst("#block-comix-grid .mini-description")?.text()?.substringAfter(heading))
            .build()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // Every page is also listed hidden for the lightbox; VIP comics end with a "Premium" banner
        return document.select("#block-image-slide a[rel=group] > img[data-src]:not([alt=Premium])").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return document.select("a[href*=/categories/]")
            .mapNotNull { it.absUrl("href").toHttpUrl().queryParameter("categori")?.takeIf(String::isNotBlank) }
            .distinct()
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Text search ignores category, tag, studio and list; of those only the first one set is used"))
                add(SortFilter())
                if (categories.isNotEmpty()) add(CategoryFilter(categories))
                add(TagFilter())
                add(StudioFilter())
                add(ListFilter())
            },
        )
    }

    private fun String.cleanTitle() = replace(TITLE_PREFIX_REGEX, "")

    companion object {
        private val COMIC_FOLDERS = setOf("comix1", "content_comix")

        // Titles start with the site's "Porn comic" label in the page's language, which the 3D category extends
        private val TITLE_PREFIX_REGEX = """^(?:porn comics 3d|porno-comics 3d|porn comic|porno comic|comic porno)\s+""".toRegex(RegexOption.IGNORE_CASE)
    }
}
