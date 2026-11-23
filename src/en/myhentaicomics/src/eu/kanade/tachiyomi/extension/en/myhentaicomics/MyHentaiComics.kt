package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class MyHentaiComics : KeiSource() {

    private val tagIdCache = ConcurrentHashMap<String, String>()

    /** Resolved page image URLs, keyed by reader page URL. */
    private val pageImageUrls = ConcurrentHashMap<String, String>()

    @Volatile
    private var indexes: TagIndexes? = null

    // =============================== Popular ================================

    // Popular = most viewed
    override suspend fun getPopularManga(page: Int): MangasPage = parseComicListing("$baseUrl/views/$page".toHttpUrl())

    // =============================== Latest =================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseComicListing("$baseUrl/gallery/$page".toHttpUrl())

    // =============================== Search =================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val resolveFilter = filters.firstInstanceOrNull<ResolveNamesFilter>()

        val hasTagGroups = filters.any {
            it is Filter.Group<*> && (it.name == CATEGORY_GROUP || it.name == GROUP_GROUP)
        }
        val selectedNames = listOf(CATEGORY_GROUP, GROUP_GROUP).flatMap { groupName ->
            (filters.firstOrNull { it.name == groupName } as? Filter.Group<*>)
                ?.state.orEmpty()
                .filterIsInstance<Filter.CheckBox>()
                .filter { it.state }
                .map { it.name }
        }

        val artistName = filters.firstInstanceOrNull<ArtistFilter>()?.state?.trim().orEmpty()
        val parodyName = filters.firstInstanceOrNull<ParodyFilter>()?.state?.trim().orEmpty()
        val artistId = artistName.takeIf(String::isNotEmpty)?.let {
            resolveName(it) ?: throw Exception("No artist named \"$it\"")
        }
        val parodyId = parodyName.takeIf(String::isNotEmpty)?.let {
            resolveName(it) ?: throw Exception("No parody named \"$it\"")
        }

        // A tapped author or parody chip arrives as a plain query, so those two names
        // still resolve; categories and groups reach us through their checkboxes, so
        // their names stay ordinary title searches.
        val resolvedQueryId = query
            .takeIf { resolveFilter?.state != false && it.isNotBlank() }
            ?.let { resolveName(it) }

        // The indexes are only needed to classify a resolved name or to turn ticked
        // checkboxes into ids; a plain title search does not pay for them.
        val tagIndexes = if (selectedNames.isNotEmpty() || resolvedQueryId != null) {
            loadTagIndexes()
        } else {
            TagIndexes(emptyList(), emptyList())
        }
        val idByName = buildMap {
            tagIndexes.categories.forEach { put(it.name, it.value) }
            tagIndexes.groups.forEach { put(it.name, it.value) }
        }
        val selectedTags = selectedNames.mapNotNull { idByName[it] }

        // Without checkboxes to hit, the filter lists are still downloading, so let the
        // name resolve rather than leave a tapped chip on an empty page.
        val queryTagId = resolvedQueryId
            ?.takeIf { !hasTagGroups || !tagIndexes.isCategoryOrGroup(it) }

        // /search accepts the query, tags and sorting together, unlike the listing paths
        val url = "$baseUrl/search/$page".toHttpUrl().newBuilder().apply {
            if (queryTagId != null) {
                addEncodedQueryParameter("tags[]", queryTagId)
            } else if (query.isNotEmpty()) {
                addQueryParameter("query", query)
            }
            selectedTags.forEach { addEncodedQueryParameter("tags[]", it) }
            artistId?.let { addEncodedQueryParameter("tags[]", it) }
            parodyId?.let { addEncodedQueryParameter("tags[]", it) }
            sortFilter?.toUriPart()?.takeIf(String::isNotEmpty)?.let {
                addQueryParameter("sorting", it)
            }
        }.build()

        return parseComicListing(url)
    }

    /**
     * Resolves a name the site's tag picker (GET /tags?term=) would suggest, so a name
     * that exactly matches becomes that tag. Hits and misses are cached per source
     * instance; paging repeats the same query.
     */
    private suspend fun resolveName(name: String): String? {
        if (name.length < MIN_TAG_QUERY_LENGTH) return null

        val key = name.lowercase()
        tagIdCache[key]?.let { return it.ifEmpty { null } }

        val url = "$baseUrl/tags".toHttpUrl().newBuilder()
            .addQueryParameter("term", name)
            .build()
        val hits = client.get(url, Fetch.XHR).parseAs<List<TagOption>>()
        val id = hits
            .firstOrNull { it.text.equals(name, ignoreCase = true) }
            ?.id
            ?.toString()
            .orEmpty()

        tagIdCache[key] = id
        return id.ifEmpty { null }
    }

    /**
     * The two axes small enough to tick: 101 categories and 111 groups. Artists and
     * parodies are an order of magnitude larger and are typed by name instead.
     */
    private suspend fun loadTagIndexes(): TagIndexes {
        indexes?.let { return it }
        return try {
            fetchTagIndexes().also { indexes = it }
        } catch (_: Exception) {
            // A failed index fetch must not take the search down with it: without the
            // indexes every tag name still resolves, only the ticked boxes lose their ids.
            TagIndexes(emptyList(), emptyList())
        }
    }

    private suspend fun fetchTagIndexes(): TagIndexes {
        val categories = client.get("$baseUrl/tag/category", Fetch.DOCUMENT).asJsoup()
            .select("div.catagory-inner a[href*='/gallery/category/']")
            .mapNotNull { el ->
                val id = el.attr("href").substringAfterLast('/')
                val name = el.selectFirst("h2")?.text()?.trim()
                if (id.isNotEmpty() && !name.isNullOrEmpty()) NamedTag(name, id) else null
            }

        val groups = client.get("$baseUrl/tag/group", Fetch.DOCUMENT).asJsoup()
            .select("#myList a.listNavItem")
            .mapNotNull { el ->
                val id = el.attr("href").substringAfterLast('/')
                val name = el.text().replace(GROUP_COUNT_REGEX, "").trim()
                if (id.isNotEmpty() && name.isNotEmpty()) NamedTag(name, id) else null
            }

        return TagIndexes(categories, groups).also { indexes = it }
    }

    // ============================== Filters =================================

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        // Throws on failure so KeiSource retries instead of caching an empty list
        val data = fetchTagIndexes()
        return FilterData(data.categories, data.groups).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val parsed = data?.parseAs<FilterData>()
            ?: indexes?.let { FilterData(it.categories, it.groups) }
        if (parsed != null) {
            indexes = TagIndexes(parsed.categories, parsed.groups)
        }

        return FilterList(
            buildList {
                add(ResolveNamesFilter())
                add(Filter.Separator())
                add(SortFilter())

                if (parsed != null && parsed.categories.isNotEmpty()) {
                    add(Filter.Separator())
                    add(TagCheckBoxGroup(CATEGORY_GROUP, parsed.categories))
                }
                if (parsed != null && parsed.groups.isNotEmpty()) {
                    add(Filter.Separator())
                    add(TagCheckBoxGroup(GROUP_GROUP, parsed.groups))
                }

                add(Filter.Separator())
                add(ArtistFilter())
                add(ParodyFilter())
            },
        )
    }

    // =========================== Comic Listing ==============================

    private suspend fun parseComicListing(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("li.item:not(.image-block) .comic-inner a").map { el ->
            SManga.create().apply {
                setUrlWithoutDomain(el.absUrl("href"))
                title = el.select("h2.comic-name").text()
                thumbnail_url = el.select("img").first()?.absUrl("src")?.encodeSpaces()
            }
        }

        val hasNextPage = document.selectFirst("li.next a") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =========================== Manga Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val descriptionDiv = document.selectFirst("div.comic-description")

        val categories = descriptionDiv
            ?.select("a[href*='/gallery/category/']")
            ?.map { it.text() }
            .orEmpty()

        val artists = descriptionDiv
            ?.select("a[href*='/gallery/artist/']")
            ?.map { it.text() }
            .orEmpty()

        val groups = descriptionDiv
            ?.select("a[href*='/gallery/group/']")
            ?.map { it.text() }
            .orEmpty()

        val parodies = descriptionDiv
            ?.select("a[href*='/gallery/parody/']")
            ?.map { it.text() }
            .orEmpty()

        val pagesText = descriptionDiv
            ?.select("div")
            ?.firstOrNull { it.ownText().startsWith("Pages:") }
            ?.text()

        val author = artists.joinToString(", ").ifEmpty { null }

        val updatedManga = manga.apply {
            title = descriptionDiv?.selectFirst("h1")?.text() ?: title
            thumbnail_url = document.selectFirst("div.comic-cover img")?.absUrl("src")?.encodeSpaces()
            this.author = author
            this.artist = author
            genre = (categories + groups + parodies).joinToString(", ")
            status = SManga.COMPLETED
            description = pagesText.orEmpty()
        }

        // Extract comic ID from the "Back to gallery" / first page link on the thumbnail page
        val firstPageHref = document.selectFirst("div.comic-cover a")?.absUrl("href")
            ?: return SMangaUpdate(updatedManga, emptyList())

        // href = "https://myhentaicomics.com/gallery/show/59109/1"
        val comicId = firstPageHref
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        val updatedChapters = listOf(
            SChapter.create().apply {
                url = "/gallery/show/$comicId/1"
                name = "Chapter 1"
                chapter_number = 1f
                date_upload = 0L
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ============================== Page List ===============================

    /**
     * Only the page numbers are produced here; each image URL is looked up when the reader
     * asks for it.
     *
     * A chapter cannot be rebuilt from one naming rule: the same work mixes stills and
     * animations, so page 3 can be "003.jpg" while page 4 is "004.gif" or "004.webp", with
     * prefixes such as "012_001.gif" or "1_001.jpg" on top. Guessing the extension of pages
     * that were not read produces 404s, so the site's own reader page is asked instead.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()

        // The comic ID from the request URL: /gallery/show/59109/1
        val comicId = document.location()
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        // The highest page number the reader links to; the site paginates 1..N with no
        // gaps, so this is also the number of images.
        val totalPages = document
            .select("ul li a[href*='/gallery/show/$comicId/']")
            .mapNotNull { it.attr("href").substringAfterLast("/").toIntOrNull() }
            .maxOrNull() ?: 1

        // The URL is the reader page, the only place that says what this page's file is.
        return (1..totalPages).map { page ->
            Page(page - 1, url = "/gallery/show/$comicId/$page")
        }
    }

    /**
     * Resolves one page's image from its reader page, which is where the site names the
     * file: the extension is per page ("004.gif" next to "003.jpg"), so nothing about the
     * neighbouring pages can be assumed.
     *
     * The reader asks for pages as they come into view, so a chapter only pays for what is
     * actually read or downloaded. Results are cached per page URL for the session because
     * scrolling back and forth would otherwise repeat the request.
     */
    override suspend fun getImageUrl(page: Page): String {
        pageImageUrls[page.url]?.let { return it }

        val pageUrl = if (page.url.startsWith("http")) page.url else baseUrl + page.url
        val imageUrl = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("ul.gallery-slide li img")
            ?.absUrl("src")
            ?.encodeSpaces()
            ?: throw Exception("No image on ${page.url}")

        pageImageUrls[page.url] = imageUrl
        return imageUrl
    }

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    private class TagIndexes(
        val categories: List<NamedTag>,
        val groups: List<NamedTag>,
    ) {
        private val categoryIds = categories.mapTo(HashSet()) { it.value }
        private val groupIds = groups.mapTo(HashSet()) { it.value }

        fun isCategoryOrGroup(id: String): Boolean = id in categoryIds || id in groupIds
    }

    companion object {
        private val GROUP_COUNT_REGEX = Regex("""\s*\(\d+\)$""")

        private const val CATEGORY_GROUP = "Categories"
        private const val GROUP_GROUP = "Groups"

        /** The site's tag picker only starts suggesting at three characters. */
        private const val MIN_TAG_QUERY_LENGTH = 3
    }
}
