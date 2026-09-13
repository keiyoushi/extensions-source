package eu.kanade.tachiyomi.multisrc.galleryadults

import android.content.SharedPreferences
import android.util.Log
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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatter

abstract class GalleryAdults :
    KeiSource(),
    ConfigurableSource {

    protected open val mangaLang: String = LANGUAGE_MULTI

    protected open val dateFormat: DateTimeFormatter? = null

    protected open val xhrHeaders get() = headersBuilder()
        .add("X-Requested-With", "XMLHttpRequest")
        .build()

    /* List detail */
    protected class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String?,
        val lang: String,
    )

    protected open val mangaTitleSelector = ".caption"

    protected open fun Element.mangaTitle(selector: String = mangaTitleSelector): String? = mangaFullTitle(selector).let {
        if (preferences.shortTitle) it?.shortenTitle() else it
    }

    protected open fun Element.mangaFullTitle(selector: String) = selectFirst(selector)?.text()

    protected open fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    protected open val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

    protected open fun Element.mangaUrl() = selectFirst(".inner_thumb a")?.attr("abs:href")

    protected open fun Element.mangaThumbnail() = selectFirst(".inner_thumb img")?.imgAttr()

    // Overwrite this to filter other languages' manga from search result.
    // Default to [mangaLang] won't filter anything
    protected open fun Element.mangaLang() = mangaLang

    protected open fun String.addPageUri(page: Int) = "${this.trim('/')}${if ('?' in this) "&" else "/?"}page=$page"

    /* Popular */
    protected open val popularMangaUrl get() = buildString {
        append("$baseUrl/")
        if (mangaLang.isNotBlank()) append("language/$mangaLang/")
        if (supportsLatest) append("popular")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = popularMangaUrl.addPageUri(page)
        return parsePopularManga(client.get(url).asJsoup())
    }

    protected open fun parsePopularManga(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector() = "div.thumb"

    protected open fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.mangaTitle()!!
        setUrlWithoutDomain(element.mangaUrl()!!)
        thumbnail_url = element.mangaThumbnail()
    }

    protected open fun popularMangaNextPageSelector(): String? = ".pagination li.active + li:not(.disabled)"

    /* Latest */
    protected open val latestUpdatesUrl get() = buildString {
        append("$baseUrl/")
        if (mangaLang.isNotBlank()) append("language/$mangaLang")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = latestUpdatesUrl.addPageUri(page)
        return parseLatestUpdates(client.get(url).asJsoup())
    }

    protected open fun parseLatestUpdates(document: Document): MangasPage {
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val hasNextPage = latestUpdatesNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun latestUpdatesSelector() = popularMangaSelector()

    protected open fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    protected open fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    /* Search */
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1) ?: return null
        return getMangaById(id)
    }

    protected open val randomEntryUrl = "$baseUrl/random/"

    protected open fun randomEntryParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val url = response.request.url.toString()
        val id = url.removeSuffix("/").substringAfterLast('/')
        return MangasPage(
            listOf(
                SManga.create().apply {
                    title = document.mangaTitle("h1")!!
                    setUrlWithoutDomain("$baseUrl/$idPrefixUri/$id/")
                    thumbnail_url = document.getCover()
                },
            ),
            false,
        )
    }

    /**
     * Manga URL: $baseUrl/$idPrefixUri/<id>/
     */
    protected open val idPrefixUri = "gallery"

    protected open suspend fun getMangaById(id: String): SManga {
        val response = client.get("$baseUrl/$idPrefixUri/$id")
        return parseMangaDetails(response.asJsoup()).apply {
            url = "/$idPrefixUri/$id/"
        }
    }

    protected open val useIntermediateSearch: Boolean = false
    protected open val supportAdvancedSearch: Boolean = false
    protected open val supportSpeechless: Boolean = false
    protected open val useBasicSearch: Boolean
        get() = !useIntermediateSearch

    protected open val searchPopularPath = "popular"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.toIntOrNull() != null) return MangasPage(listOf(getMangaById(query)), false)

        val randomEntryFilter = filters.firstInstanceOrNull<RandomEntryFilter>()
        if (randomEntryFilter?.state == true) return randomEntryParse(client.get(randomEntryUrl))

        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val genresFilter = filters.firstInstanceOrNull<GenresFilter>()
        val selectedGenres = genresFilter?.selected ?: emptyList()
        val favoriteFilter = filters.firstInstanceOrNull<FavoriteFilter>()

        // Speechless
        val speechlessFilter = filters.firstInstanceOrNull<SpeechlessFilter>()

        // Advanced search
        val advancedSearchFilters = filters.filterIsInstance<AdvancedTextFilter>()

        if (favoriteFilter?.state == true) return searchFavoriteFilter(page, query, filters)

        val url = when {
            supportSpeechless && speechlessFilter?.state == true ->
                speechlessFilterSearchUrl(page, query, filters)

            supportAdvancedSearch && advancedSearchFilters.any { it.state.isNotBlank() } ->
                advancedSearchUrl(page, query, filters)

            selectedGenres.size == 1 && query.isBlank() ->
                tagBrowsingSearchUrl(page, query, filters)

            useIntermediateSearch ->
                intermediateSearchUrl(page, query, filters)

            useBasicSearch && (selectedGenres.size > 1 || query.isNotBlank()) ->
                basicSearchUrl(page, query, filters)

            sortOrderFilter?.state == 1 ->
                latestUpdatesUrl

            else -> popularMangaUrl
        }

        return parseSearchManga(client.get(url.addPageUri(page)))
    }

    /**
     * Browsing user's personal favorites saved on site. This requires login in view WebView.
     */
    protected open suspend fun searchFavoriteFilter(page: Int, query: String, filters: FilterList) = parseSearchManga(
        client.post(
            "$baseUrl/$favoritePath",
            xhrHeaders,
            FormBody.Builder()
                .add("page", page.toString())
                .build(),
        ),
    )

    protected open val basicSearchKey = "q"

    /**
     * Basic Search: support query string with multiple-genres filter by adding genres to query string.
     */
    protected open fun basicSearchUrl(page: Int, query: String, filters: FilterList): String {
        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val genresFilter = filters.firstInstanceOrNull<GenresFilter>()
        val selectedGenres = genresFilter?.selected ?: emptyList()

        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search/")
            addEncodedQueryParameter(basicSearchKey, buildQueryString(selectedGenres.map { it.name }, query))
            if (sortOrderFilter?.state == 0) addQueryParameter("sort", "popular")
        }.build().toString()
    }

    protected open val intermediateSearchKey = "key"

    /**
     * This supports filter query search with languages, categories (manga, doujinshi...)
     * with additional sort orders.
     */
    protected open fun intermediateSearchUrl(page: Int, query: String, filters: FilterList): String {
        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val genresFilter = filters.firstInstanceOrNull<GenresFilter>()
        val selectedGenres = genresFilter?.selected ?: emptyList()

        // Intermediate search
        val categoryFilters = filters.firstInstanceOrNull<CategoryFilters>()

        // Only for query string or multiple tags
        return "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            getSortOrderURIs().forEachIndexed { index, pair ->
                addQueryParameter(pair.second, toBinary(sortOrderFilter?.state == index))
            }
            categoryFilters?.state?.forEach {
                addQueryParameter(it.uri, toBinary(it.state))
            }
            getLanguageURIs().forEach { pair ->
                addQueryParameter(
                    pair.second,
                    toBinary(mangaLang == pair.first || mangaLang == LANGUAGE_MULTI),
                )
            }
            addEncodedQueryParameter(intermediateSearchKey, buildQueryString(selectedGenres.map { it.name }, query))
        }.build().toString()
    }

    protected open val advancedSearchKey = "key"
    protected open val advancedSearchUri = "advsearch"

    /**
     * Advanced Search normally won't support search for string but allow to include/exclude specific
     * tags/artists/groups/parodies/characters
     */
    protected open fun advancedSearchUrl(page: Int, query: String, filters: FilterList): String {
        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val genresFilter = filters.firstInstanceOrNull<GenresFilter>()
        val selectedGenres = genresFilter?.selected ?: emptyList()

        // Intermediate search
        val categoryFilters = filters.firstInstanceOrNull<CategoryFilters>()
        // Advanced search
        val advancedSearchFilters = filters.filterIsInstance<AdvancedTextFilter>()

        return "$baseUrl/$advancedSearchUri/".toHttpUrl().newBuilder().apply {
            getSortOrderURIs().forEachIndexed { index, pair ->
                addQueryParameter(pair.second, toBinary(sortOrderFilter?.state == index))
            }
            categoryFilters?.state?.forEach {
                addQueryParameter(it.uri, toBinary(it.state))
            }
            getLanguageURIs().forEach { pair ->
                addQueryParameter(
                    pair.second,
                    toBinary(
                        mangaLang == pair.first ||
                            mangaLang == LANGUAGE_MULTI,
                    ),
                )
            }

            // Build this query string: +tag:"bat+man"+-tag:"cat"+artist:"Joe"...
            // +tag must be encoded into %2Btag while the rest are not needed to encode
            val keys = emptyList<String>().toMutableList()
            keys.addAll(selectedGenres.map { "%2Btag:\"${it.name}\"" })
            advancedSearchFilters.forEach { filter ->
                val key = when (filter) {
                    is TagsFilter -> "tag"
                    is ParodiesFilter -> "parody"
                    is ArtistsFilter -> "artist"
                    is CharactersFilter -> "character"
                    is GroupsFilter -> "group"
                    else -> null
                }
                if (key != null) {
                    keys.addAll(
                        filter.state.trim()
                            .replace(regexSpaceNotAfterComma, "+")
                            .replace(" ", "")
                            .split(',')
                            .mapNotNull {
                                val match = regexExcludeTerm.find(it)
                                match?.groupValues?.let { groups ->
                                    "${if (groups[1].isNotBlank()) "-" else "%2B"}$key:\"${groups[2]}\""
                                }
                            },
                    )
                }
            }
            addEncodedQueryParameter(advancedSearchKey, keys.joinToString("+"))
        }.build().toString()
    }

    /**
     * Convert space( ) typed in search-box into plus(+) in URL. Then:
     * - uses plus(+) to search for exact match
     * - use comma(, ) for separate terms, as AND condition.
     * Plus(+) after comma(, ) doesn't have any effect.
     */
    protected open fun buildQueryString(tags: List<String>, query: String): String = (tags + query).filter(String::isNotBlank)
        .joinToString(",") {
            // any space except after a comma (we're going to replace spaces only between words)
            it.trim()
                .replace(regexSpaceNotAfterComma, "+")
                .replace(" ", "")
        }

    protected open fun tagBrowsingSearchUrl(page: Int, query: String, filters: FilterList): String {
        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()
        val genresFilter = filters.firstInstanceOrNull<GenresFilter>()
        val selectedGenres = genresFilter?.selected ?: emptyList()

        // Browsing single tag's catalog
        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("tag")
            addPathSegment(selectedGenres.single().uri)
            if (sortOrderFilter?.state == 0) addPathSegment(searchPopularPath)
        }
            .build().toString()
    }

    /**
     * Browsing speechless titles. Some sites exclude speechless titles from normal search and
     * allow browsing separately.
     */
    protected open fun speechlessFilterSearchUrl(page: Int, query: String, filters: FilterList): String {
        // Basic search
        val sortOrderFilter = filters.firstInstanceOrNull<SortOrderFilter>()

        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("language")
            addPathSegment(LANGUAGE_SPEECHLESS)
            if (sortOrderFilter?.state == 0) addPathSegment(searchPopularPath)
        }.build().toString()
    }

    protected open val favoritePath = "user/fav_pags.php"

    protected open fun loginRequired(document: Document, url: String): Boolean = (
        url.contains("/login/") &&
            document.select("input[value=Login]").isNotEmpty()
        )

    protected open fun parseSearchManga(response: Response): MangasPage {
        val document = response.asJsoup()
        if (loginRequired(document, response.request.url.toString())) {
            throw Exception("Log in via WebView to view favorites")
        } else {
            val hasNextPage = searchMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
            val mangas = document.select(searchMangaSelector())
                .searchMangaFromElements(hasNextPage)
            return MangasPage(mangas, hasNextPage)
        }
    }

    protected open fun Elements.searchMangaFromElements(hasNextPage: Boolean): List<SManga> = mapNotNull {
        SMangaDto(
            title = it.mangaTitle() ?: return@mapNotNull null,
            url = it.mangaUrl() ?: return@mapNotNull null,
            thumbnail = it.mangaThumbnail(),
            lang = it.mangaLang(),
        )
    }
        .let { unfiltered ->
            val results = unfiltered.filter {
                mangaLang.isBlank() ||
                    it.lang == LANGUAGE_SPEECHLESS || // Include Speechless in search results
                    it.lang == mangaLang
            }
            // return at least 1 title if all mangas in current page is of other languages
            if (results.isEmpty() && hasNextPage) {
                unfiltered.firstOrNull()?.let(::listOf) ?: emptyList()
            } else {
                results
            }
        }
        .map {
            SManga.create().apply {
                title = it.title
                setUrlWithoutDomain(it.url)
                thumbnail_url = it.thumbnail
            }
        }

    protected open fun searchMangaSelector() = popularMangaSelector()

    protected open fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    /* Related titles */
    override val supportsRelatedMangas = true

    protected open fun relatedMangaSelector() = popularMangaSelector()

    override suspend fun fetchRelatedMangaList(manga: SManga) = client.get(getMangaUrl(manga)).asJsoup()
        .select(relatedMangaSelector())
        .searchMangaFromElements(hasNextPage = false)

    /* Details */

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val doc = response.asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(doc),
            chapters = parseChapterList(doc, response.request.url.encodedPath),
        )
    }

    protected open val mangaDetailInfoSelector = ".gallery_top"

    protected open fun parseMangaDetails(document: Document): SManga = document.selectFirst(mangaDetailInfoSelector)!!.run {
        SManga.create().apply {
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
            mangaTitle("h1")?.let { title = it }
            thumbnail_url = getCover()
            genre = getInfo("Tags")
            author = getInfo("Artists").ifEmpty { getInfo("Groups") }
            description = getDescription(document)
        }
    }

    protected open fun Element.getCover() = selectFirst(".cover img")?.imgAttr()

    protected abstract fun getInfoSelector(tag: String): String

    protected open fun Element.infoTagName() = ownText()

    /**
     * Parsing document to extract info related to [tag].
     */
    protected open fun Element.getInfo(tag: String): String = select(getInfoSelector(tag))
        .flatMap {
            listOf(
                it.infoTagName(),
                it.select(".split_tag").text()
                    .removePrefix("| ")
                    .trim(),
            )
        }
        .filter(String::isNotBlank)
        .sorted()
        .joinToString()

    protected open fun Element.getDescription(document: Document? = null): String = (
        listOf("Parodies", "Characters", "Groups", "Languages", "Categories", "Category")
            .mapNotNull { tag ->
                getInfo(tag)
                    .takeIf { it.isNotBlank() }
                    ?.let { "**$tag**: $it" }
            } +
            listOfNotNull(
                getInfoPages(document),
                getInfoAlternativeTitle(),
                getInfoFullTitle(),
            )
        )
        .joinToString("\n\n")

    protected open fun Element.getInfoPages(document: Document? = null): String? = document?.inputIdValueOf(totalPagesSelector)
        ?.takeIf { it.isNotBlank() }
        ?.let { "**Pages**: $it" }

    protected open fun Element.getInfoAlternativeTitle(): String? = selectFirst("h1 + h2, .subtitle")?.ownText()
        .takeIf { !it.isNullOrBlank() }
        ?.let { "**Alternative title**: $it" }

    protected open fun Element.getInfoFullTitle(): String? = if (preferences.shortTitle) "**Full title**: ${mangaFullTitle("h1")}" else null

    protected open fun Element.getTime(): Long = selectFirst(".uploaded")
        ?.ownText()
        .toDate(dateFormat)

    /* Chapters */
    protected open fun parseChapterList(document: Document, url: String): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            scanlator = document.selectFirst(mangaDetailInfoSelector)
                ?.getInfo("Groups")
            date_upload = document.getTime()
            setUrlWithoutDomain(url)
        },
    )

    /* Pages */
    protected open fun Element.inputIdValueOf(string: String): String = select("input[id=$string]").attr("value")

    protected open val pagesRequest = "inc/thumbs_loader.php"
    protected open val galleryIdSelector = "gallery_id"
    protected open val loadIdSelector = "load_id"
    protected open val loadDirSelector = "load_dir"
    protected open val totalPagesSelector = "load_pages"
    protected open val serverSelector = "load_server"

    protected open fun pageRequestForm(document: Document, totalPages: String, loadedPages: Int): FormBody {
        val token = document.select("[name=csrf-token]").attr("content")
        val serverNumber = document.serverNumber()

        return FormBody.Builder()
            .add("u_id", document.inputIdValueOf(galleryIdSelector))
            .add("g_id", document.inputIdValueOf(loadIdSelector))
            .add("img_dir", document.inputIdValueOf(loadDirSelector))
            .add("visible_pages", loadedPages.toString())
            .add("total_pages", totalPages)
            .add("type", "2") // 1 would be "more", 2 is "all remaining"
            .apply {
                if (token.isNotBlank()) add("_token", token)
                if (serverNumber != null) add("server", serverNumber)
            }
            .build()
    }

    protected open val thumbnailSelector = ".gallery_thumb"

    protected open val serverPrefix = "m"

    protected open fun Element.getServer(): String {
        val domain = baseUrl.toHttpUrl().host
        return serverNumber()
            ?.let { "$serverPrefix$it.$domain" }
            ?: getCover()!!.toHttpUrl().host
    }

    protected open fun Element.serverNumber(): String? = inputIdValueOf(serverSelector)
        .takeIf { it.isNotBlank() }

    protected open fun Element.parseJson(): String? = selectFirst("script:containsData(parseJSON)")?.data()
        ?.substringAfter("$.parseJSON('")
        ?.substringBefore("');")?.trim()

    /**
     * Page URL: $baseUrl/$pageUri/<id>/<page>
     */
    protected open val pageUri = "g"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        return pageListParse(response.asJsoup())
    }

    protected open suspend fun pageListParse(document: Document): List<Page> {
        val json = document.parseJson()

        if (json != null) {
            val loadDir = document.inputIdValueOf(loadDirSelector)
            val loadId = document.inputIdValueOf(loadIdSelector)
            val galleryId = document.inputIdValueOf(galleryIdSelector)
            val pageUrl = "$baseUrl/$pageUri/$galleryId"

            val server = document.getServer()
            val imagesUri = "https://$server/$loadDir/$loadId"

            try {
                val pages = mutableListOf<Page>()
                val images = json.parseAs<JsonObject>()

                // JSON string in this form: {"1":"j,1100,1148","2":"j,728,689",...
                for (image in images) {
                    val ext = image.value.toString().replace("\"", "").split(",")[0]
                    val imageExt = when (ext) {
                        "p" -> "png"
                        "b" -> "bmp"
                        "g" -> "gif"
                        "w" -> "webp"
                        else -> "jpg"
                    }
                    val idx = image.key.toInt()
                    pages.add(
                        Page(
                            index = idx,
                            imageUrl = "$imagesUri/${image.key}.$imageExt",
                            url = "$pageUrl/$idx/",
                        ),
                    )
                }
                return pages
            } catch (_: SerializationException) {
                Log.e("GalleryAdults", "Failed to decode JSON")
                return this.pageListParseAlternative(document)
            }
        } else {
            return this.pageListParseAlternative(document)
        }
    }

    /**
     * Overwrite this to force extension not blindly converting thumbnails to full image
     * by simply removing the trailing "t" from file name. Instead, it will open each page,
     * one by one, then parsing for actual image's URL.
     * This will be much slower but guaranteed work.
     *
     * This only apply if site doesn't provide 'parseJSON'.
     */
    protected open val parsingImagePageByPage: Boolean = false

    /**
     * Either:
     *  - Load all thumbnails then convert thumbnails to full images.
     *  - Or request then parse for a list of manga's page's URL,
     *   which will then request one by one to parse for page's image's URL using [imageUrlParse].
     */
    protected open suspend fun pageListParseAlternative(document: Document): List<Page> {
        val totalPages = document.inputIdValueOf(totalPagesSelector)
        val galleryId = document.inputIdValueOf(galleryIdSelector)
        val pageUrl = "$baseUrl/$pageUri/$galleryId"

        val pages = document.select("$thumbnailSelector a")
            .mapNotNull {
                if (parsingImagePageByPage) {
                    it.absUrl("href")
                } else {
                    it.selectFirst("img")?.imgAttr() ?: return@mapNotNull null
                }
            }
            .toMutableList()

        if (totalPages.isNotBlank() && totalPages.toInt() > pages.size) {
            val form = pageRequestForm(document, totalPages, pages.size)

            val morePages = client.post("$baseUrl/$pagesRequest", xhrHeaders, form).use { it ->
                it.asJsoup()
                    .select("a")
                    .mapNotNull {
                        if (parsingImagePageByPage) {
                            it.absUrl("href")
                        } else {
                            it.selectFirst("img")?.imgAttr() ?: return@mapNotNull null
                        }
                    }
            }
            if (morePages.isNotEmpty()) {
                pages.addAll(morePages)
            } else {
                return pageListParseDummy(document)
            }
        }

        return pages.mapIndexed { idx, url ->
            if (parsingImagePageByPage) {
                Page(idx, url)
            } else {
                Page(
                    index = idx,
                    imageUrl = url.thumbnailToFull(),
                    url = "$pageUrl/$idx/",
                )
            }
        }
    }

    /**
     * Generate all images using `totalPages`. Supposedly they are sequential.
     * Use in case any extension doesn't know how to request for "All thumbnails"
     */
    protected open fun pageListParseDummy(document: Document): List<Page> {
        val loadDir = document.inputIdValueOf(loadDirSelector)
        val loadId = document.inputIdValueOf(loadIdSelector)
        val galleryId = document.inputIdValueOf(galleryIdSelector)
        val pageUrl = "$baseUrl/$pageUri/$galleryId"

        val server = document.getServer()
        val imagesUri = "https://$server/$loadDir/$loadId"

        val images = document.select("$thumbnailSelector img")
        val thumbUrls = images.map { it.imgAttr() }.toMutableList()

        val totalPages = document.inputIdValueOf(totalPagesSelector)

        if (totalPages.isNotBlank() && totalPages.toInt() > thumbUrls.size) {
            val imagesExt = images.first()?.imgAttr()!!
                .substringAfterLast('.')

            thumbUrls.addAll(
                listOf((images.size + 1)..totalPages.toInt()).flatten().map {
                    "$imagesUri/${it}t.$imagesExt"
                },
            )
        }
        return thumbUrls.mapIndexed { idx, url ->
            Page(
                index = idx,
                imageUrl = url.thumbnailToFull(),
                url = "$pageUrl/$idx/",
            )
        }
    }

    override suspend fun getImageUrl(page: Page): String = imageUrlParse(client.get(page.url).asJsoup())

    protected open fun imageUrlParse(document: Document): String = document.selectFirst("img#gimg, img#fimg")?.imgAttr() ?: ""

    /* Filters */

    protected open val maxTagPages = 5

    protected open val tagsPath = "tags/popular"

    /**
     * Parsing [document] to return tags in <name: uri> Map.
     */
    protected open fun tagsParser(document: Document) = document.select("a.tag_btn")
        .associate {
            it.select(".list_tag, .tag_name").text() to
                it.attr("href")
                    .removeSuffix("/").substringAfterLast('/')
        }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = mutableMapOf<String, String>()

        (1..maxTagPages).map { page ->
            async {
                runCatching {
                    val tagsUrl = "$baseUrl/$tagsPath".addPageUri(page)
                    client.get(tagsUrl).asJsoup()
                }.getOrNull()?.let { doc ->
                    genres.putAll(tagsParser(doc))
                }
            }
        }.awaitAll()

        genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Map<String, String>>().orEmpty()

        val filters = emptyList<Filter<*>>().toMutableList()
        if (useIntermediateSearch) {
            filters.add(Filter.Header("HINT: Separate search term with comma (,)"))
        }

        filters.add(SortOrderFilter(getSortOrderURIs()))

        if (genres.isNotEmpty()) {
            filters.add(GenresFilter(genres))
        }

        if (useIntermediateSearch || supportAdvancedSearch) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    CategoryFilters(getCategoryURIs()),
                ),
            )
        }

        if (supportAdvancedSearch) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    Filter.Header("Advanced filters will ignore query search. Separate terms by comma (,) and precede term with minus (-) to exclude."),
                    TagsFilter(),
                    ParodiesFilter(),
                    ArtistsFilter(),
                    CharactersFilter(),
                    GroupsFilter(),
                ),
            )
        }

        filters.add(Filter.Separator())

        if (supportSpeechless) {
            filters.add(SpeechlessFilter())
        }
        filters.add(FavoriteFilter())

        filters.add(RandomEntryFilter())

        return FilterList(filters)
    }

    protected open fun getSortOrderURIs() = listOf(
        Pair("Popular", "pp"),
        Pair("Latest", "lt"),
    ) + if (useIntermediateSearch || supportAdvancedSearch) {
        listOf(
            Pair("Downloads", "dl"),
            Pair("Top Rated", "tr"),
        )
    } else {
        emptyList()
    }

    protected open fun getCategoryURIs() = listOf(
        SearchFlagFilter("Manga", "m"),
        SearchFlagFilter("Doujinshi", "d"),
        SearchFlagFilter("Western", "w"),
        SearchFlagFilter("Image Set", "i"),
        SearchFlagFilter("Artist CG", "a"),
        SearchFlagFilter("Game CG", "g"),
    )

    protected open fun getLanguageURIs() = listOf(
        Pair(LANGUAGE_ENGLISH, "en"),
        Pair(LANGUAGE_JAPANESE, "jp"),
        Pair(LANGUAGE_SPANISH, "es"),
        Pair(LANGUAGE_FRENCH, "fr"),
        Pair(LANGUAGE_KOREAN, "kr"),
        Pair(LANGUAGE_GERMAN, "de"),
        Pair(LANGUAGE_RUSSIAN, "ru"),
    )

    /* Preferences */
    protected val preferences: SharedPreferences by getPreferencesLazy()

    protected open val useShortTitlePreference = true

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHORT_TITLE
            title = "Display Short Titles"
            summaryOff = "Showing Long Titles"
            summaryOn = "Showing short Titles"
            setDefaultValue(false)
            setVisible(useShortTitlePreference)
        }.also(screen::addPreference)
    }

    protected val SharedPreferences.shortTitle
        get() = getBoolean(PREF_SHORT_TITLE, false)

    companion object {
        private const val PREF_SHORT_TITLE = "pref_short_title"

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
