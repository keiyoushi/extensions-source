package eu.kanade.tachiyomi.extension.en.tailspace

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.devalue.decodeDevalue
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Tailspace :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 5, period = 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/browse.data".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "trending")
            .addQueryParameter("page", page.toString())
            .build()
        return getBrowseManga(url, page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/browse.data".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return getBrowseManga(url, page)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse.data".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query.trim())
        }

        for (filter in filters) {
            when (filter) {
                is SortFilter -> {
                    val sortValue = filter.selectedValue
                    if (sortValue.isNotEmpty()) {
                        url.addQueryParameter("sort", sortValue)
                    }
                }
                is CategoryGroup -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("c", it.value)
                    }
                }
                is StatusFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("finishedOnly", "true")
                    }
                }
                is SfwFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("sfwOnly", "true")
                    }
                }
                is TagGroup -> {
                    for (tag in filter.state) {
                        if (tag.isIncluded()) {
                            url.addQueryParameter("tag", tag.id.toString())
                        } else if (tag.isExcluded()) {
                            url.addQueryParameter("excludeTag", tag.id.toString())
                        }
                    }
                }
                else -> {}
            }
        }

        return getBrowseManga(url.build(), page)
    }

    private suspend fun getBrowseManga(url: HttpUrl, page: Int): MangasPage {
        val browsePage = client.get(url).decodeDevalueAs<BrowsePageDto>(route = "routes/pages/browse/BrowsePage")
        val data = browsePage.data ?: return MangasPage(emptyList(), false)

        val mangas = data.comicsAndAds.orEmpty().mapNotNull { item ->
            val id = item.id?.longOrNull ?: return@mapNotNull null
            val name = item.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artistName = item.artistName
            val thumbnailVersion = item.thumbnailVersion ?: 0
            val state = item.state
            val category = item.category
            val isSfw = item.isSfw
            val tags = item.tags.orEmpty().mapNotNull { it.name }

            SManga.create().apply {
                this.url = comicUrl(name)
                title = name
                thumbnail_url = "https://pics.tailspace.com/comics/$id/thumbnail-2x.webp?v=$thumbnailVersion"
                artist = artistName
                author = artistName
                genre = buildList {
                    category?.let { add(it) }
                    when (isSfw) {
                        true -> add("Safe-ish")
                        false -> add("Explicit")
                        null -> {}
                    }
                    tags.forEach { add(it) }
                }.distinct().joinToString(", ").ifEmpty { null }
                status = when (state) {
                    "finished" -> SManga.COMPLETED
                    "wip" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }

        val hasNextPage = page < data.numberOfPages
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (!url.encodedPath.startsWith("/c/")) return null
        val comicName = url.pathSegments.getOrNull(1)?.removeSuffix(".data")?.takeIf { it.isNotEmpty() }
            ?: return null
        return SManga.create().apply {
            this.url = comicUrl(comicName)
            title = comicName
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val currentComic = getComicDetails(manga.url)
        val shouldCoalesce = preferences.getBoolean(PREF_COALESCE_SERIES, true)
        val hasLinks = currentComic.previousComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() } != null ||
            currentComic.nextComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() } != null
        val allComics = if (shouldCoalesce && (fetchChapters || (fetchDetails && hasLinks))) {
            getAllCoalescedComics(currentComic)
        } else {
            listOf(currentComic)
        }

        val firstComic = allComics.first()
        val latestComic = allComics.last()

        if (fetchDetails) {
            manga.apply {
                title = firstComic.name
                thumbnail_url = "https://pics.tailspace.com/comics/${firstComic.id}/thumbnail-2x.webp?v=${firstComic.thumbnailVersion ?: 0}"
                artist = firstComic.artists
                author = firstComic.artists
                genre = buildSet {
                    for (comic in allComics) {
                        comic.category?.let { add(it) }
                        when (comic.isSfw) {
                            true -> add("Safe-ish")
                            false -> add("Explicit")
                            null -> {}
                        }
                        for (tag in comic.tags.orEmpty()) {
                            tag.name?.let { add(it) }
                        }
                    }
                }.joinToString(", ").ifEmpty { null }
                description = allComics.firstNotNullOfOrNull { it.description?.takeIf(String::isNotBlank) }
                    ?.let(::extractDescriptionText)
                status = when {
                    allComics.any { it.state == "wip" } -> SManga.ONGOING
                    allComics.all { it.state == "finished" } -> SManga.COMPLETED
                    else -> when (latestComic.state) {
                        "finished" -> SManga.COMPLETED
                        "wip" -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                }
                initialized = true
            }
        }

        val chapterList = if (fetchChapters) {
            val isSingle = allComics.size == 1
            allComics.mapIndexed { index, chapterComic ->
                SChapter.create().apply {
                    url = comicUrl(chapterComic.name)
                    name = if (isSingle) "Chapter" else chapterComic.name
                    chapter_number = (index + 1).toFloat()
                    date_upload = chapterComic.published ?: chapterComic.updated ?: 0L
                    scanlator = chapterComic.artists
                }
            }.reversed()
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun comicUrl(name: String): String = "/c/" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private suspend fun getAllCoalescedComics(currentComic: ComicDetails): List<ComicDetails> {
        val visitedIds = mutableSetOf(currentComic.id)
        val chapters = ArrayDeque<ComicDetails>()
        chapters.add(currentComic)

        var prev = currentComic.previousComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() }
        while (prev != null && visitedIds.add(prev.id)) {
            val prevName = prev.name ?: break
            val comic = runCatching { getComicDetails(comicUrl(prevName)) }.getOrNull() ?: break
            chapters.addFirst(comic)
            prev = comic.previousComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() }
        }

        var next = currentComic.nextComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() }
        while (next != null && visitedIds.add(next.id)) {
            val nextName = next.name ?: break
            val comic = runCatching { getComicDetails(comicUrl(nextName)) }.getOrNull() ?: break
            chapters.addLast(comic)
            next = comic.nextComic?.takeIf { it.id != 0L && !it.name.isNullOrBlank() }
        }

        return chapters.toList()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val comic = getComicDetails(chapter.url)
        return comic.pages.orEmpty()
            .sortedBy { it.pageNumber }
            .mapIndexed { index, pageItem ->
                Page(index, imageUrl = "https://pics.tailspace.com/comics/${comic.id}/${pageItem.token}.jpg")
            }
    }

    private suspend fun getComicDetails(urlOrName: String): ComicDetails {
        val clean = urlOrName.removeSuffix(".data")
        val requestPath = when {
            clean.startsWith("/c/") -> clean.removePrefix("/")
            clean.startsWith("c/") -> clean
            else -> "c/" + URLEncoder.encode(clean.removePrefix("/"), "UTF-8").replace("+", "%20")
        }
        val requestUrl = "$baseUrl/$requestPath.data"
        val comicPage = client.get(requestUrl).decodeDevalueAs<ComicPageDto>(route = "routes/pages/comic/ComicPage")
        return comicPage.data?.comic ?: throw Exception("Could not find comic in ComicPage data")
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/tags").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            CategoryGroup(),
            StatusFilter(),
            SfwFilter(),
        )

        val tags = runCatching { data?.parseAs<TagsResponseDto>()?.data }
            .getOrNull()
            ?: runCatching { data?.parseAs<List<TagDto>>() }
                .getOrNull()

        if (!tags.isNullOrEmpty()) {
            val general = tags.filter { it.category == null }.sortedBy { it.name }
            val species = tags.filter { it.category == "species" }.sortedBy { it.name }
            val character = tags.filter { it.category == "character" }.sortedBy { it.name }
            val franchise = tags.filter { it.category == "franchise" }.sortedBy { it.name }

            if (general.isNotEmpty()) filters.add(TagGroup("General Tags", general))
            if (species.isNotEmpty()) filters.add(TagGroup("Species Tags", species))
            if (character.isNotEmpty()) filters.add(TagGroup("Character Tags", character))
            if (franchise.isNotEmpty()) filters.add(TagGroup("Franchise Tags", franchise))
        } else {
            filters.add(Filter.Header("Press 'Filter' to load tags"))
        }

        return FilterList(filters)
    }

    private fun extractDescriptionText(rawDesc: String): String {
        if (!rawDesc.trimStart().startsWith("{")) return rawDesc
        return runCatching {
            val root = rawDesc.parseAs<JsonElement>()
            val sb = StringBuilder()
            fun traverse(element: JsonElement) {
                when (element) {
                    is JsonObject -> {
                        if (element["type"]?.stringOrNull == "text") {
                            element["text"]?.stringOrNull?.let { sb.append(it) }
                        } else {
                            element["content"]?.let { traverse(it) }
                            if (element["type"]?.stringOrNull in listOf("paragraph", "heading", "listItem")) {
                                sb.append("\n\n")
                            }
                        }
                    }
                    is JsonArray -> {
                        for (child in element) {
                            traverse(child)
                        }
                    }
                    else -> {}
                }
            }
            traverse(root)
            sb.toString().trim()
        }.getOrDefault(rawDesc)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_COALESCE_SERIES
            title = "Coalesce Series"
            summary = "Combine linked chapters of a series into a single entry"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private inline fun <reified T> Response.decodeDevalueAs(route: String): T {
        val decoded = body.string().decodeDevalue()
            ?: throw IllegalStateException("Failed to decode Devalue response")
        val routeElement = decoded.jsonObject[route]
            ?: throw IllegalStateException("Route '$route' not found in Devalue response")
        return routeElement.parseAs()
    }

    companion object {
        private const val PREF_COALESCE_SERIES = "pref_coalesce_series"
    }
}
