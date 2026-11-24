package eu.kanade.tachiyomi.extension.en.myhentaicomics

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Source
abstract class MyHentaiComics : KeiSource() {

    private val tagIdCache = ConcurrentHashMap<String, String>()

    /** Ranked suggestions, keyed by the lowercased name that missed. */
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()

    /** Every tag name the site has, fetched on the first miss so a typo does not pay twice. */
    @Volatile
    private var siteTags: List<TagOption>? = null

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
            resolveOrFail("artist", it)
        }
        val parodyId = parodyName.takeIf(String::isNotEmpty)?.let {
            resolveOrFail("parody", it)
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

        val hits = searchTags(name) ?: return null
        val id = hits
            .firstOrNull { it.text.equals(name, ignoreCase = true) }
            ?.id
            ?.toString()
            .orEmpty()

        tagIdCache[key] = id
        return id.ifEmpty { null }
    }

    /**
     * Turns a typed name into a tag id or fails with something the user can act on.
     *
     * An exact name wins. Anything else is reported with the closest spellings from the
     * whole catalogue rather than a bare "not found", because the site's own picker is a
     * substring search that offers nothing at all once a name is misspelled.
     */
    private suspend fun resolveOrFail(kind: String, name: String): String {
        tagIdCache[name.lowercase()]?.takeIf { it.isNotEmpty() }?.let { return it }

        val hits = searchTags(name) ?: failWith(unverifiedMessage(kind, name))
        hits.firstOrNull { it.text.equals(name, ignoreCase = true) }?.let {
            tagIdCache[name.lowercase()] = it.id.toString()
            return it.id.toString()
        }

        // Nothing matches it, so it is a typo: rank the whole catalogue instead.
        val tags = loadSiteTags() ?: failWith(unverifiedMessage(kind, name))
        failWith(notFoundMessage(kind, name, closest(tags, name)))
    }

    /**
     * Every tag name on the site in one request: 12k entries, ~120 KB gzipped. Fetched
     * only once a name has already failed to resolve, and kept for the source instance;
     * an empty `term` is how the site's picker lists its whole catalogue.
     */
    private suspend fun loadSiteTags(): List<TagOption>? {
        siteTags?.let { return it }
        return searchTags("")?.also { siteTags = it }
    }

    /** The site's autocomplete; null when the lookup itself failed (offline, rate limit). */
    private suspend fun searchTags(term: String): List<TagOption>? = try {
        val url = "$baseUrl/tags".toHttpUrl().newBuilder()
            .addQueryParameter("term", term)
            .build()
        client.get(url, Fetch.XHR).parseAs<List<TagOption>>()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Closest names to a typo, by optimal string alignment so adjacent transpositions
     * count as one edit. The site's picker is a substring search and returns nothing for
     * a misspelled name, so ranking the full catalogue is what turns "derpixonn" back
     * into "Derpixon" instead of guessing at a prefix.
     */
    private fun closest(tags: List<TagOption>, input: String): List<String> {
        if (input.length < MIN_SUGGESTION_LENGTH) return emptyList()

        val key = input.lowercase()
        suggestionCache[key]?.let { return it }

        val limit = maxEdits(input.length)
        val close = tags.asSequence()
            .filter { abs(it.text.length - input.length) <= limit }
            .map { it.text to editDistance(input, it.text) }
            .filter { (name, distance) ->
                distance <= limit && distance.toDouble() / maxOf(input.length, name.length) <= MAX_FUZZY_RATIO
            }
            .sortedWith(compareBy({ it.second }, { it.first.length }))
            .take(MAX_SUGGESTIONS)
            .map { it.first }
            .toList()

        suggestionCache[key] = close
        return close
    }

    /** One edit for short names, two for medium, three for long; nonsense stays nonsense. */
    private fun maxEdits(length: Int): Int = when {
        length <= 5 -> 1
        length <= 9 -> 2
        else -> 3
    }

    /** Levenshtein plus adjacent transpositions, on rolling rows to spare the allocator. */
    private fun editDistance(first: String, second: String): Int {
        val a = first.lowercase()
        val b = second.lowercase()
        var twoBack = IntArray(b.length + 1)
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    current[j] = minOf(current[j], twoBack[j - 2] + 1)
                }
            }
            val spare = twoBack
            twoBack = previous
            previous = current
            current = spare
        }

        return previous[b.length]
    }

    private fun notFoundMessage(kind: String, name: String, suggestions: List<String>): String {
        val hint = suggestions
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ") { annotated(it) }
            ?.let { " Did you mean: $it?" }
            .orEmpty()
        val advice = if (hint.isEmpty()) " Check the spelling, or clear the field to drop this filter." else ""
        return "No $kind named \"$name\" on MyHentaiComics.$hint$advice"
    }

    /**
     * Names the filter index files under another namespace, so a suggestion that is a
     * category is not offered as an artist. Silent until the filter lists are loaded.
     */
    private fun annotated(name: String): String = indexes?.kindOf(name)?.let { "$name ($it)" } ?: name

    private fun unverifiedMessage(kind: String, name: String): String = "Couldn't look up the $kind \"$name\" because MyHentaiComics is unreachable. Try again when you're online."

    private fun toast(message: String) {
        try {
            val context = Injekt.get<Application>()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "toast failed: ${e.message}")
        }
    }

    /** The thrown message is what the browse screen shows; the toast makes it visible now. */
    private fun failWith(message: String): Nothing {
        toast(message)
        throw Exception(message)
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
        // KeiSource guarantees at least one of the flags is set. Both kinds of data live
        // on the same page, so one request serves either of them.
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val descriptionDiv = document.selectFirst("div.comic-description")

        val updatedManga = if (fetchDetails) {
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

            manga.apply {
                title = descriptionDiv?.selectFirst("h1")?.text() ?: title
                thumbnail_url = document.selectFirst("div.comic-cover img")?.absUrl("src")?.encodeSpaces()
                this.author = author
                this.artist = author
                genre = (categories + groups + parodies).joinToString(", ")
                status = SManga.COMPLETED
                description = pagesText.orEmpty()
            }
        } else {
            manga
        }

        // Rebuilt only when the caller asked for chapters; otherwise the existing list is
        // returned as is, which the SMangaUpdate contract allows.
        val updatedChapters = if (fetchChapters) {
            // Extract comic ID from the "Back to gallery" / first page link on the page
            val firstPageHref = document.selectFirst("div.comic-cover a")?.absUrl("href")

            if (firstPageHref == null) {
                emptyList()
            } else {
                // href = "https://myhentaicomics.com/gallery/show/59109/1"
                val comicId = firstPageHref
                    .substringAfter("/gallery/show/")
                    .substringBefore("/")

                listOf(
                    SChapter.create().apply {
                        url = "/gallery/show/$comicId/1"
                        name = "Chapter 1"
                        chapter_number = 1f
                        date_upload = 0L
                    },
                )
            }
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    /**
     * Lets a pasted site URL work from the search box, which otherwise treats the text as
     * a query. Series pages and reader pages both point at the same work, so both are
     * accepted and the series page is the canonical [SManga.url].
     */
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val galleryIndex = segments.indexOf("gallery")
        if (galleryIndex < 0) return null

        val kind = segments.getOrNull(galleryIndex + 1) ?: return null
        if (kind != "thumbnails" && kind != "show") return null

        val comicId = segments.getOrNull(galleryIndex + 2)
            ?.takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) }
            ?: return null

        val canonicalUrl = "/gallery/thumbnails/$comicId"
        val document = client.get("$baseUrl$canonicalUrl", Fetch.DOCUMENT).asJsoup()
        val title = document.selectFirst("div.comic-description h1")?.text() ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(canonicalUrl)
            this.title = title
            thumbnail_url = document.selectFirst("div.comic-cover img")?.absUrl("src")?.encodeSpaces()
        }
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
        val slide = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            .selectFirst("ul.gallery-slide")

        // Every page is an image today; a <video> page would at least have a poster to
        // show, since the reader has no video player (unlike the site's own reader).
        val imageUrl = slide?.selectFirst("img")
            ?.absUrl("src")
            ?: slide?.selectFirst("video[poster]")?.absUrl("poster")
            ?: throw Exception("No image on ${page.url}")

        val encoded = imageUrl.encodeSpaces()

        pageImageUrls[page.url] = encoded
        return encoded
    }

    // ============================== Helpers =================================

    private fun String.encodeSpaces(): String = replace(" ", "%20")

    private class TagIndexes(
        val categories: List<NamedTag>,
        val groups: List<NamedTag>,
    ) {
        private val categoryIds = categories.mapTo(HashSet()) { it.value }
        private val groupIds = groups.mapTo(HashSet()) { it.value }
        private val kindByName = buildMap {
            categories.forEach { put(it.name.lowercase(), "category") }
            groups.forEach { put(it.name.lowercase(), "group") }
        }

        fun isCategoryOrGroup(id: String): Boolean = id in categoryIds || id in groupIds

        fun kindOf(name: String): String? = kindByName[name.lowercase()]
    }

    companion object {
        private val GROUP_COUNT_REGEX = Regex("""\s*\(\d+\)$""")

        private const val CATEGORY_GROUP = "Categories"
        private const val GROUP_GROUP = "Groups"

        /** Two, not the picker's three: the endpoint matches shorter names, and the
         * catalogue has artists called "Fu", "IT" and the like. */
        private const val MIN_TAG_QUERY_LENGTH = 2

        /** Shorter typos have too many neighbours for a suggestion to be a guess worth making. */
        private const val MIN_SUGGESTION_LENGTH = 3

        private const val MAX_SUGGESTIONS = 3

        private const val MAX_FUZZY_RATIO = 0.34
    }
}

private const val LOG_TAG = "MyHentaiComics"
