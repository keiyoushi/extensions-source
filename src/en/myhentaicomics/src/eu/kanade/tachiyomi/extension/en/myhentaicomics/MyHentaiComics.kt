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

    /** Ranked suggestions, keyed by the field and the lowercased name that missed. */
    private val suggestionCache = ConcurrentHashMap<String, List<String>>()

    /** Every tag name the site has, fetched on the first miss so a typo does not pay twice. */
    @Volatile
    private var siteTags: List<TagOption>? = null

    /** Which names are artists, parodies, categories and groups; null until a miss needs it. */
    @Volatile
    private var namespaces: Namespaces? = null

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

        val key = cacheKey("term", name)
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
     * Only an exact name of the field's own kind is accepted. "LewdDoodler" is an artist,
     * so it does not belong in the Parody field even though the site's picker returns it
     * for either, and a category belongs in its checkbox rather than in a name field. Every
     * other result is a miss: the closest names from this field's namespace are offered
     * first, the other namespace only when this one has nothing, and a name that exists
     * elsewhere gets told where it belongs.
     */
    private suspend fun resolveOrFail(field: String, name: String): String {
        val key = cacheKey(field, name)
        tagIdCache[key]?.takeIf { it.isNotEmpty() }?.let { return it }

        val hits = searchTags(name) ?: failWith(unverifiedMessage(field, name))
        // The picker matches case-insensitively except across an internal capital: it finds
        // "LewdDoodler" but not "lewdoodler". The catalogue has no such blind spot, so a
        // name that is typed correctly in the wrong case is still recognised as exact.
        val exact = hits.firstOrNull { it.text.equals(name, ignoreCase = true) }
            ?: loadSiteTags()?.firstOrNull { it.text.equals(name, ignoreCase = true) }

        if (exact != null) {
            val kind = loadNamespaces()?.kindOf(exact.text)
            // Without the namespace lists the site's exact match is the best answer left.
            if (kind == null || kind == field) {
                tagIdCache[key] = exact.id.toString()
                return exact.id.toString()
            }
            failWith(misplacedMessage(field, exact.text, kind))
        }

        suggestionsFor(field, name).takeIf { it.isNotEmpty() }
            ?.let { failWith(notFoundMessage(field, name, it)) }

        suggestionsFor(otherField(field), name).takeIf { it.isNotEmpty() }
            ?.let { failWith(crossFieldMessage(field, name, it)) }

        failWith(unknownMessage(field, name))
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

    /**
     * Which names are parodies, categories and groups; everything else is an artist.
     *
     * The picker returns all four in one untyped list and /gallery/<type>/<id> ignores the
     * type segment, so the split is rebuilt from the parody index plus the filter indexes:
     * those three lists plus the artists make up the whole catalogue, which leaves every
     * other name an artist. Null when an index cannot be read, which only costs the wording.
     */
    private suspend fun loadNamespaces(): Namespaces? {
        namespaces?.let { return it }
        val parodies = fetchParodyNames() ?: return null
        loadTagIndexes()
        val indexes = indexes ?: return null
        return Namespaces(
            parodies = parodies,
            categories = indexes.categories.mapTo(HashSet()) { it.name.lowercase() },
            groups = indexes.groups.mapTo(HashSet()) { it.name.lowercase() },
        ).also { namespaces = it }
    }

    private suspend fun fetchParodyNames(): Set<String>? = try {
        client.get("$baseUrl/tag/parody", Fetch.DOCUMENT).asJsoup()
            .select("a[href*='/gallery/parody/']")
            .mapTo(HashSet()) { it.text().replace(TAG_COUNT_REGEX, "").trim().lowercase() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
     * Names close to a typo, taken from one namespace at a time.
     *
     * Distance comes first, so a transposition beats an unrelated longer name, and names
     * that contain the whole input follow, because "melkor mancin" is not a typo of
     * "Romulo Melkor Mancin" — it is the part of the name the user remembers.
     */
    private suspend fun suggestionsFor(field: String, input: String): List<String> {
        if (input.length < MIN_SUGGESTION_LENGTH) return emptyList()

        val key = "$field\u0000${input.lowercase()}"
        suggestionCache[key]?.let { return it }

        val all = loadSiteTags() ?: return emptyList()
        val namespaces = loadNamespaces()
        val candidates = all.asSequence()
            .map { it.text }
            .filter { namespaces == null || namespaces.kindOf(it) == field }
            .toList()

        val limit = maxEdits(input.length)
        val ranked = candidates.asSequence()
            .filter { abs(it.length - input.length) <= limit }
            .map { it to editDistance(input, it) }
            .filter { (candidate, distance) ->
                distance <= limit && distance.toDouble() / maxOf(input.length, candidate.length) <= MAX_FUZZY_RATIO
            }
            .sortedWith(compareBy({ it.second }, { it.first.length }))
            .map { it.first }

        val closest = ranked

        val contained = candidates.asSequence()
            .filter { it.length > input.length && it.contains(input, ignoreCase = true) }

        val close = (closest + contained)
            .distinctBy { it.lowercase() }
            .take(MAX_SUGGESTIONS)
            .toList()

        suggestionCache[key] = close
        return close
    }

    /** One edit for four-character names, two from five up, three for long ones. */
    private fun maxEdits(length: Int): Int = when {
        length <= 4 -> 1
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

    private fun notFoundMessage(field: String, name: String, suggestions: List<String>): String {
        val shown = suggestions.joinToString(", ") { label(field, it) }
        return "No $field named \"$name\" on MyHentaiComics. Did you mean: $shown?"
    }

    /**
     * Nothing in this field's namespace comes close but the other one does. Naming the
     * namespace is the point: a parody pasted into the Artist field should be moved, not
     * silently used.
     */
    private fun crossFieldMessage(field: String, name: String, suggestions: List<String>): String {
        val other = otherField(field)
        val heading = if (suggestions.size == 1) "Closest $other" else "Closest ${other}s"
        return "No $field named \"$name\" on MyHentaiComics. $heading: ${suggestions.joinToString(", ")}. " +
            "Try the ${other.replaceFirstChar { it.uppercase() }} field."
    }

    /** A name that exists, but as something this field cannot filter. */
    private fun misplacedMessage(field: String, name: String, kind: String): String = when (kind) {
        "category" ->
            "\"$name\" is a category on MyHentaiComics, not ${article(field)}. Tick it in the Categories filter instead."
        "group" ->
            "\"$name\" is a group on MyHentaiComics, not ${article(field)}. Tick it in the Groups filter instead."
        else ->
            "\"$name\" is ${article(kind)} on MyHentaiComics, not ${article(field)}. " +
                "Move it to the ${kind.replaceFirstChar { it.uppercase() }} field."
    }

    private fun unknownMessage(field: String, name: String): String = "No $field named \"$name\" on MyHentaiComics. Check the spelling, or clear the field to drop this filter."

    private fun otherField(field: String) = if (field == "artist") "parody" else "artist"

    /** Marks a suggestion the filter index files outside the field, when the ranking had to guess. */
    private fun label(field: String, name: String): String = indexes?.kindOf(name)?.takeIf { it != field }?.let { "$name ($it)" } ?: name

    private fun article(word: String) = if (word.firstOrNull()?.let { it in "aeiou" } == true) "an $word" else "a $word"

    private fun unverifiedMessage(field: String, name: String): String = "Couldn't look up the $field \"$name\" because MyHentaiComics is unreachable. Try again when you're online."

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
                val name = el.text().replace(TAG_COUNT_REGEX, "").trim()
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

    /** Resolved ids are per caller: a name accepted as an artist is not accepted as a parody. */
    private fun cacheKey(scope: String, name: String) = "$scope\u0000${name.lowercase()}"

    /**
     * The name spaces, as lowercased sets. `/gallery/<type>/<id>` answers 200 for every id,
     * so this map — not the URL — is what can say "that name is an artist".
     */
    private class Namespaces(
        private val parodies: Set<String>,
        private val categories: Set<String>,
        private val groups: Set<String>,
    ) {
        fun kindOf(name: String): String? {
            val key = name.lowercase()
            return when (key) {
                in parodies -> "parody"
                in categories -> "category"
                in groups -> "group"
                // Every catalogue name is in one of the four lists, so the rest are artists.
                else -> "artist"
            }
        }
    }

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
        private val TAG_COUNT_REGEX = Regex("""\s*\(\d+\)$""")

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
