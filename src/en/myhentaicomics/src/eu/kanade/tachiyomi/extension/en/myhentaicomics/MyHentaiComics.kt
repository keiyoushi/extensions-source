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
     * Builds the page list, then checks the naming scheme it derived against the last
     * page before trusting it. Works that do not fit any scheme fall back to reading
     * each reader page, which is slower but cannot produce broken image URLs.
     */
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()

        // The comic ID from the request URL: /gallery/show/59109/1
        val comicId = document.location()
            .substringAfter("/gallery/show/")
            .substringBefore("/")

        // The first page's image carries the naming scheme: directory, prefix, padding,
        // suffix and extension. Example: ".../original/012_001.gif?22".
        val firstImageUrl = document.selectFirst("ul.gallery-slide li img")?.absUrl("src")
            ?: return emptyList()

        // The highest page number the reader links to; the site paginates 1..N with no
        // gaps, so this is also the number of images.
        val totalPages = document
            .select("ul li a[href*='/gallery/show/$comicId/']")
            .mapNotNull { it.attr("href").substringAfterLast("/").toIntOrNull() }
            .maxOrNull() ?: 1

        // A one-page work needs no scheme: the URL we already have is the whole list.
        if (totalPages <= 1) {
            return listOf(Page(0, imageUrl = firstImageUrl.encodeSpaces()))
        }

        val secondImageUrl = imageUrlOnPage(chapterUrl, 2)
        val template = secondImageUrl?.let { pageNameTemplate(firstImageUrl, it) }

        if (template != null) {
            // Page 2 alone cannot tell a scheme like "001_002" (first number moves) from
            // "012_001" (second number moves), or a 3-digit counter from a 4-digit one,
            // because both fit a naive guess. The last page is the cheapest independent
            // check: if it matches its generated name, every page in between will too.
            val lastImageUrl = imageUrlOnPage(chapterUrl, totalPages)
            val lastName = lastImageUrl?.substringAfterLast('/')
            if (lastName == null || lastName != template.name(totalPages)) {
                return pageListByReadingPages(chapterUrl, totalPages)
            }

            return (1..totalPages).mapIndexed { index, page ->
                Page(index, imageUrl = template.url(page).encodeSpaces())
            }
        }

        return pageListByReadingPages(chapterUrl, totalPages)
    }

    /** The image the reader shows for [page] of [chapterUrl], or null if that fails. */
    private suspend fun imageUrlOnPage(chapterUrl: String, page: Int): String? {
        // Chapters are stored as ".../gallery/show/<comic>/1"; swap the page segment.
        val url = chapterUrl.substringBeforeLast('/') + "/" + page
        return try {
            client.get(url, Fetch.DOCUMENT).asJsoup()
                .selectFirst("ul.gallery-slide li img")
                ?.absUrl("src")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Last-resort page list: ask the reader for every page. Costs one request per page
     * (up to a few hundred), so it only runs when the derived scheme failed its check.
     */
    private suspend fun pageListByReadingPages(chapterUrl: String, totalPages: Int): List<Page> = (1..totalPages).mapNotNull { page ->
        imageUrlOnPage(chapterUrl, page)?.let { Page(page - 1, imageUrl = it.encodeSpaces()) }
    }

    /**
     * Works out how page images of one work are named by comparing the first two.
     *
     * The catalogue carries several layouts, e.g. "001.jpg", "001_002.jpg" (first number
     * moves), "012_001.gif" (the prefix stays, second number moves), "1_001.jpg" (no
     * padding) and the legacy "0001.jpg" (four digits). Comparing the two names tells us
     * which run of digits is the page number and which parts are constant; the width of
     * the page-2 run gives the zero padding, because its number is known to be two.
     *
     * Returns null when the names differ in anything but a single number that goes up by
     * one, or when they do not share a directory.
     */
    private fun pageNameTemplate(firstUrl: String, secondUrl: String): PageNameTemplate? {
        val directory = firstUrl.substringBeforeLast('/') + "/"
        if (secondUrl.substringBeforeLast('/') + "/" != directory) return null

        val first = firstUrl.substringAfterLast('/')
        val second = secondUrl.substringAfterLast('/')
        val firstParts = first.splitIntoRuns()
        val secondParts = second.splitIntoRuns()
        if (firstParts.size != secondParts.size) return null

        val changed = firstParts.indices.filter { firstParts[it] != secondParts[it] }
        if (changed.size != 1) return null

        val firstRun = firstParts[changed.single()]
        val secondRun = secondParts[changed.single()]
        if (!firstRun.all(Char::isDigit) || !secondRun.all(Char::isDigit)) return null
        // Page 2's number must be page 1's plus one, otherwise the changed run is not the
        // page counter (a cover image could, for instance, be named differently).
        if (firstRun.toIntOrNull()?.plus(1) != secondRun.toIntOrNull()) return null

        return PageNameTemplate(
            directory = directory,
            prefix = secondParts.take(changed.single()).joinToString(""),
            suffix = secondParts.drop(changed.single() + 1).joinToString(""),
            width = secondRun.length,
        )
    }

    /** Splits a name into alternating runs of digits and everything else, in order. */
    private fun String.splitIntoRuns(): List<String> = NAME_RUN_REGEX.findAll(this).map { it.value }.toList()

    /**
     * How the images of one work are named: [prefix], the page number zero-padded to
     * [width], then [suffix], all inside [directory].
     */
    private data class PageNameTemplate(
        val directory: String,
        val prefix: String,
        val suffix: String,
        val width: Int,
    ) {
        fun name(page: Int): String = prefix + page.toString().padStart(width, '0') + suffix

        fun url(page: Int): String = directory + name(page)
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

        /** Runs of digits or non-digits inside a page image file name. */
        private val NAME_RUN_REGEX = Regex("""\d+|\D+""")

        private const val CATEGORY_GROUP = "Categories"
        private const val GROUP_GROUP = "Groups"

        /** The site's tag picker only starts suggesting at three characters. */
        private const val MIN_TAG_QUERY_LENGTH = 3
    }
}
