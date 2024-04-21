package eu.kanade.tachiyomi.multisrc.galleryadults

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.imgAttr
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.toDate
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat

abstract class GalleryAdults(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "all",
    protected open val mangaLang: String = LANGUAGE_MULTI,
    protected val simpleDateFormat: SimpleDateFormat? = null,
) : ConfigurableSource, ParsedHttpSource() {

    override val supportsLatest = false

    protected open val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val SharedPreferences.parseImages
        get() = getBoolean(PREF_PARSE_IMAGES, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_PARSE_IMAGES
            title = "Parse for images' URL one by one (Might help if chapter failed to load some pages)"
            summaryOff = "Fast images' URL generator"
            summaryOn = "Slowly parsing images' URL"
            setDefaultValue(false)
        }.also(screen::addPreference)

        addRandomUAPreferenceToScreen(screen)
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                userAgentType = preferences.getPrefUAType(),
                customUA = preferences.getPrefCustomUA(),
                filterInclude = listOf("chrome"),
            )
            .rateLimit(4)
            .build()
    }

    protected open val xhrHeaders = headers.newBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    /* List detail */
    protected class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String?,
        val lang: String,
    )

    protected open fun Element.mangaTitle(selector: String = ".caption"): String? =
        selectFirst(selector)?.text()

    protected open fun Element.mangaUrl() =
        selectFirst(".inner_thumb a")?.attr("abs:href")

    protected open fun Element.mangaThumbnail() =
        selectFirst(".inner_thumb img")?.imgAttr()

    // Overwrite this to filter other languages' manga from search result.
    // Default to [mangaLang] won't filter anything
    protected open fun Element.mangaLang() = mangaLang

    protected open fun HttpUrl.Builder.addPageUri(page: Int): HttpUrl.Builder {
        val url = toString()
        if (!url.endsWith('/') && !url.contains('?')) {
            addPathSegment("") // trailing slash (/)
        }
        if (page > 1) addQueryParameter("page", page.toString())
        return this
    }

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (mangaLang.isNotBlank()) addPathSegments("language/$mangaLang")
            if (supportsLatest) addPathSegment("popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaSelector() = "div.thumb"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.mangaTitle()!!
            setUrlWithoutDomain(element.mangaUrl()!!)
            thumbnail_url = element.mangaThumbnail()
        }
    }

    override fun popularMangaNextPageSelector() = ".pagination li.active + li:not(.disabled)"

    /* Latest */
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (mangaLang.isNotBlank()) addPathSegments("language/$mangaLang")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, id) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    protected open fun searchMangaByIdRequest(id: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(idPrefixUri)
            addPathSegments("$id/")
        }
        return GET(url.build(), headers)
    }

    protected open fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/$idPrefixUri/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sortFilter = filters.filterIsInstance<SortFilter>().firstOrNull()
        val genresFilter = filters.filterIsInstance<GenresFilter>().firstOrNull()
        val selectedGenres = genresFilter?.state?.filter { it.state } ?: emptyList()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()
        return when {
            favoriteFilter?.state == true -> {
                val url = "$baseUrl/$favoritePath".toHttpUrl().newBuilder()
                return POST(
                    url.build().toString(),
                    xhrHeaders,
                    FormBody.Builder()
                        .add("page", page.toString())
                        .build(),
                )
            }
            selectedGenres.size == 1 && query.isBlank() -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("tag")
                    addPathSegment(selectedGenres.single().uri)
                    if (sortFilter?.state == 0) addPathSegment("popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
            selectedGenres.size > 1 || query.isNotBlank() -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegments("search/")
                    addEncodedQueryParameter("q", buildQueryString(selectedGenres.map { it.name }, query))
                    // Search results sorting is not supported by AsmHentai
                    if (sortFilter?.state == 0) addQueryParameter("sort", "popular")
                    addPageUri(page)
                }
                GET(url.build(), headers)
            }
            else -> popularMangaRequest(page)
        }
    }

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - uses plus(+) to search for exact match
     * - use comma(,) for separate terms, as AND condition.
     * Plus(+) after comma(,) doesn't have any effect.
     */
    protected open fun buildQueryString(tags: List<String>, query: String): String {
        return (tags + query).filterNot { it.isBlank() }.joinToString(",") {
            // any space except after a comma (we're going to replace spaces only between words)
            it.trim()
                .replace(Regex("""(?<!,)\s+"""), "+")
                .replace(" ", "")
        }
    }

    protected open val favoritePath = "includes/user_favs.php"

    protected open fun loginRequired(document: Document, url: String): Boolean {
        return (
            url.contains("/login/") &&
                document.select("input[value=Login]").isNotEmpty()
            )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (loginRequired(document, response.request.url.toString())) {
            throw Exception("Log in via WebView to view favorites")
        } else {
            val mangas = document.select(searchMangaSelector())
                .map {
                    SMangaDto(
                        title = it.mangaTitle()!!,
                        url = it.mangaUrl()!!,
                        thumbnail = it.mangaThumbnail(),
                        lang = it.mangaLang(),
                    )
                }
                .let { unfiltered ->
                    if (mangaLang.isNotBlank()) unfiltered.filter { it.lang == mangaLang } else unfiltered
                }
                .map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.thumbnail
                    }
                }

            return MangasPage(mangas, document.select(searchMangaNextPageSelector()).isNotEmpty())
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    /* Details */
    protected open fun Element.getCover() =
        selectFirst(".cover img")?.imgAttr()

    protected open fun Element.getInfo(tag: String): String {
        return select("ul.${tag.lowercase()} a")
            .joinToString { it.ownText() }
    }

    protected open fun Element.getDescription(): String = (
        listOf("Parodies", "Characters", "Languages", "Categories")
            .mapNotNull { tag ->
                getInfo(tag)
                    .let { if (it.isNotBlank()) "$tag: $it" else null }
            } +
            listOfNotNull(
                selectFirst(".pages:contains(Pages:)")?.ownText(),
            )
        )
        .joinToString("\n")

    protected open fun Element.getTime(): Long {
        return selectFirst("#main-info > div.tag-container > time")
            ?.attr("datetime")
            ?.replace("T", " ")
            .toDate(simpleDateFormat)
    }

    protected open val mangaDetailInfoSelector = ".gallery_top"

    override fun mangaDetailsParse(document: Document): SManga {
        return document.selectFirst(mangaDetailInfoSelector)!!.run {
            SManga.create().apply {
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
                title = mangaTitle("h1")!!
                thumbnail_url = getCover()
                genre = getInfo("Tags")
                author = getInfo("Artists")
                description = getDescription()
            }
        }
    }

    /* Chapters */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = document.selectFirst(mangaDetailInfoSelector)
                    ?.getInfo("Groups")
                date_upload = document.getTime()
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    /* Pages */
    protected open fun Document.inputIdValueOf(string: String): String {
        return select("input[id=$string]").attr("value")
    }

    protected open val galleryIdSelector = "gallery_id"
    protected open val loadIdSelector = "load_id"
    protected open val loadDirSelector = "load_dir"
    protected open val totalPagesSelector = "load_pages"
    protected open val pageUri = "g"
    protected open val pageSelector = ".gallery_thumb"

    override fun pageListParse(document: Document): List<Page> {
        return if (preferences.parseImages) {
            pageListRequest(document)
        } else {
            val galleryId = document.inputIdValueOf(galleryIdSelector)
            val totalPages = document.inputIdValueOf(totalPagesSelector)
            val pageUrl = "$baseUrl/$pageUri/$galleryId"
            val imageUrl = document.selectFirst("$pageSelector img")?.imgAttr()!!
            val imageUrlPrefix = imageUrl.substringBeforeLast('/')
            val imageUrlSuffix = imageUrl.substringAfterLast('.')
            return listOf(1..totalPages.toInt()).flatten().map {
                Page(
                    index = it,
                    imageUrl = "$imageUrlPrefix/$it.$imageUrlSuffix",
                    url = "$pageUrl/$it/",
                )
            }
        }
    }

    /**
     * Method to request then parse for a list of manga's page's URL,
     * which will then request one by one to parse for page's image's URL.
     * This method will be used when user set in preference.
     */
    protected open fun pageListRequest(document: Document): List<Page> =
        throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#gimg, img#fimg")?.imgAttr()!!
    }

    /* Filters */
    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }
    private var tagsFetchAttempt = 0
    private var genres = emptyList<Genre>()

    protected open fun tagsRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tags/popular")
            addPageUri(page)
        }
        return GET(url.build(), headers)
    }

    protected open fun tagsParser(document: Document): List<Pair<String, String>> {
        return document.select(".list_tags .tag_item")
            .mapNotNull {
                Pair(
                    it.selectFirst("h3.list_tag")?.ownText() ?: "",
                    it.select("a").attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    protected open fun getGenres() {
        if (genres.isEmpty() && tagsFetchAttempt < 3) {
            launchIO {
                val tags = mutableListOf<Pair<String, String>>()
                runBlocking {
                    val jobsPool = mutableListOf<Job>()
                    // Get first 3 pages
                    (1..3).forEach { page ->
                        jobsPool.add(
                            launchIO {
                                runCatching {
                                    tags.addAll(
                                        client.newCall(tagsRequest(page))
                                            .execute().asJsoup().let { tagsParser(it) },
                                    )
                                }
                            },
                        )
                    }
                    jobsPool.joinAll()
                    genres = tags.sortedWith(compareBy { it.first }).map { Genre(it.first, it.second) }
                }

                tagsFetchAttempt++
            }
        }
    }

    override fun getFilterList(): FilterList {
        getGenres()
        return FilterList(
            SortFilter(),
            Filter.Separator(),

            if (genres.isEmpty()) {
                Filter.Header("Press 'reset' to attempt to load tags")
            } else {
                GenresFilter(genres)
            },
            Filter.Separator(),

            FavoriteFilter(),
        )
    }

    // Top 50 tags
    private class Genre(name: String, val uri: String) : Filter.CheckBox(name)
    private class GenresFilter(genres: List<Genre>) : Filter.Group<Genre>(
        "Tag filter",
        genres.map { Genre(it.name, it.uri) },
    )

    private class SortFilter : Filter.Select<String>(
        "Sort order",
        listOf("Popular", "Latest").toTypedArray(),
    )

    private class FavoriteFilter : Filter.CheckBox("Show favorites only (login via WebView)", false)

    protected open val idPrefixUri = "g"

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val PREF_PARSE_IMAGES = "pref_parse_images"

        // references to be used in factory
        const val LANGUAGE_MULTI = ""
        const val LANGUAGE_ENGLISH = "english"
        const val LANGUAGE_JAPANESE = "japanese"
        const val LANGUAGE_CHINESE = "chinese"
        const val LANGUAGE_KOREAN = "korean"
        const val LANGUAGE_SPANISH = "spanish"
        const val LANGUAGE_FRENCH = "french"
        const val LANGUAGE_GERMAN = "german"
        const val LANGUAGE_RUSSIAN = "russian"
        const val LANGUAGE_SPEECHLESS = "speechless"
        const val LANGUAGE_TRANSLATED = "translated"
    }
}
