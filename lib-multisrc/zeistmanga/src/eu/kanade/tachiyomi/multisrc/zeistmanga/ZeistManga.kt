package eu.kanade.tachiyomi.multisrc.zeistmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import kotlin.time.Instant

abstract class ZeistManga : KeiSource() {

    protected open val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    protected open val mangaCategory = "Series"

    open fun apiUrl(feed: String = mangaCategory): HttpUrl.Builder = "$baseUrl/feeds/posts/default/-/".toHttpUrl().newBuilder()
        .addPathSegment(feed)
        .addQueryParameter("alt", "json")

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("ar", "en", "es", "id", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    // Popular
    open fun popularMangaUrl(page: Int) = baseUrl

    protected open val popularMangaSelector = "div.PopularPosts div.grid > figure"
    protected open val popularMangaSelectorTitle = "figcaption > a"
    protected open val popularMangaSelectorUrl = "figcaption > a"

    override suspend fun getPopularManga(page: Int) = if (supportsLatest) {
        parsePopularManga(client.get(popularMangaUrl(page)))
    } else {
        getLatestUpdates(page)
    }

    open fun parsePopularManga(response: Response): MangasPage {
        val mangas = response.asJsoup().select(popularMangaSelector).map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
                title = element.selectFirst(popularMangaSelectorTitle)!!.text()
                setUrlWithoutDomain(element.selectFirst(popularMangaSelectorUrl)!!.attr("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest
    open fun latestUpdatesUrl(page: Int, orderBy: String? = "published"): String {
        val startIndex = MAX_MANGA_RESULTS * (page - 1) + 1
        return apiUrl()
            .addQueryParameter("orderby", orderBy)
            .addQueryParameter("max-results", (MAX_MANGA_RESULTS + 1).toString())
            .addQueryParameter("start-index", startIndex.toString())
            .build().toString()
    }

    override suspend fun getLatestUpdates(page: Int) = parseSearchManga(client.get(latestUpdatesUrl(page)))

    open fun parseLastestUpdates(response: Response) = parseSearchManga(response)

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size < 3) return null
        val doc = client.get(url).asJsoup()
        return mangaDetailsParse(doc).apply {
            if (title.isEmpty()) {
                title = doc.selectFirst("meta[property='og:title']")!!.attr("content")
                    .substringBefore("-").trim()
            }
            setUrlWithoutDomain(url.toString())
        }
    }

    open fun searchMangaUrl(page: Int, query: String) = apiUrl()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val startIndex = MAX_MANGA_RESULTS * (page - 1) + 1
        val url = searchMangaUrl(page, query)

        val searchUrl = if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
                .addQueryParameter("max-results", (MAX_MANGA_RESULTS + 1).toString())
                .addQueryParameter("start-index", startIndex.toString())
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is StatusList -> {
                        url.addPathSegment(filter.selected.value)
                    }

                    is TypeList -> {
                        url.addPathSegment(filter.selected.value)
                    }

                    is LanguageList -> {
                        url.addPathSegment(filter.selected.value)
                    }

                    is GenreList -> {
                        filter.state.forEach { genre ->
                            when (genre.state) {
                                true -> url.addPathSegment(genre.value)
                                false -> {}
                            }
                        }
                    }

                    else -> {}
                }
            }
            url
        }.build().toString().replaceLast("q=", "q=label:$mangaCategory+")

        return parseSearchManga(client.get(searchUrl))
    }

    protected open val excludedCategories: List<String> = listOf("Anime", "Novel", "Novela")

    open fun parseSearchManga(response: Response): MangasPage {
        val result = response.parseAs<ZeistMangaDto>()
        val mangas = result.feed?.entry.orEmpty()
            .filter { it.category.orEmpty().any { category -> category.term == mangaCategory } }
            .filterNot { it.category.orEmpty().any { category -> excludedCategories.contains(category.term) } }
            .map { it.toSManga(baseUrl) }

        val mangalist = mangas.toMutableList()
        if (mangas.size == MAX_MANGA_RESULTS + 1) {
            mangalist.removeAt(mangalist.lastIndex)
            return MangasPage(mangalist, true)
        }

        return MangasPage(mangalist, false)
    }

    // Details + Chapters
    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val feedUrl = manga.memo["feedUrl"]?.parseAs<FeedUrl>()
        return if (feedUrl?.isValid() == true && supportsChapterFeed) {
            coroutineScope {
                val mangaDeferred = async {
                    if (fetchDetails) {
                        val doc = client.get(getMangaUrl(manga)).asJsoup()
                        mangaDetailsParse(doc).apply { memo = manga.memo }
                    } else {
                        manga
                    }
                }
                val chaptersDeferred = async { if (fetchChapters) getChapterList(feedUrl.url) else chapters }
                SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
            }
        } else {
            val doc = client.get(getMangaUrl(manga)).asJsoup()
            val url = getChapterFeedUrl(doc, manga.title)

            val updatedManga = mangaDetailsParse(doc).apply {
                memo = buildJsonObject {
                    put(
                        "feedUrl",
                        FeedUrl(
                            url,
                            useOldChapterFeed,
                            useNewChapterFeed,
                            chapterCategory,
                        ).toJsonElement(),
                    )
                }
            }
            SMangaUpdate(
                manga = updatedManga,
                chapters = if (fetchChapters) getChapterList(url, doc) else chapters,
            )
        }
    }

    protected open val statusSelectorList = listOf(
        "Status",
        "Estado",
        "الحالة",
    )

    protected open val authorSelectorList = listOf(
        "Author",
        "Autor",
        "Mangaka",
        "الكاتب",
        "Yazar",
    )

    protected open val artisSelectorList = listOf(
        "Artist",
        "Artista",
        "الرسام",
        "Çizer",
    )

    protected open val mangaDetailsSelector = ".grid.gtc-235fr"
    protected open val mangaDetailsSelectorThumbnail = "img"
    protected open val mangaDetailsSelectorDescription = "#synopsis"
    protected open val mangaDetailsSelectorGenres = "div.mt-15 > a[rel=tag]"
    protected open val mangaDetailsSelectorAuthor = "span#author"
    protected open val mangaDetailsSelectorArtist = "span#artist"
    protected open val mangaDetailsSelectorAltName = "header > p"
    protected open val mangaDetailsSelectorStatus = "span[data-status]"
    protected open val mangaDetailsSelectorInfo = ".y6x11p"
    protected open val mangaDetailsSelectorInfoTitle = "strong"
    protected open val mangaDetailsSelectorInfoDescription = "span.dt"

    open fun mangaDetailsParse(document: Document): SManga {
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = profileManga.selectFirst(mangaDetailsSelectorThumbnail)!!.attr("abs:src")
            description = buildString {
                append(profileManga.select(mangaDetailsSelectorDescription).text())
                append("\n\n")
                profileManga.selectFirst(mangaDetailsSelectorAltName)?.text()?.takeIf { it.isNotBlank() }?.let {
                    append(intl["alternative_names"])
                    append(it)
                }
            }.trim()
            genre = profileManga.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }
            author = profileManga.selectFirst(mangaDetailsSelectorAuthor)?.text()
            artist = profileManga.selectFirst(mangaDetailsSelectorArtist)?.text()
            status = parseStatus(profileManga.selectFirst(mangaDetailsSelectorStatus)?.text() ?: "")

            val infoElement = profileManga.select(mangaDetailsSelectorInfo)
            infoElement.forEach { element ->
                val infoText = element.ownText().trim().ifEmpty { element.selectFirst(mangaDetailsSelectorInfoTitle)?.text()?.trim() ?: "" }
                val descText = element.select(mangaDetailsSelectorInfoDescription).text().trim()
                when {
                    status == SManga.UNKNOWN && statusSelectorList.any { infoText.contains(it) } -> {
                        status = parseStatus(descText)
                    }

                    author == null && authorSelectorList.any { infoText.contains(it) } -> {
                        author = descText
                    }

                    artist == null && artisSelectorList.any { infoText.contains(it) } -> {
                        artist = descText
                    }
                }
            }
        }
    }

    protected open val chapterCategory: String = "Chapter"

    open val preferChapterUpdatedDate: Boolean = false

    open suspend fun fetchChapter(url: String, startIndex: Int, maxResults: Int): ZeistMangaDto {
        val paginationUrl = url.toHttpUrl().newBuilder()
            .setQueryParameter("start-index", startIndex.toString())
            .setQueryParameter("max-results", maxResults.toString()).build().toString()

        val res = client.get(paginationUrl)
        return res.parseAs<ZeistMangaDto>()
    }

    open suspend fun getChapterList(feedUrl: String, doc: Document? = null): List<SChapter> {
        val allEntries = mutableListOf<ZeistMangaEntryDto>()

        // Get total and server chunk size by initial max request.
        val result = fetchChapter(feedUrl, 1, MAX_CHAPTER_RESULTS)
        val totalResults = result.feed?.totalResults?.t?.toIntOrNull() ?: MAX_CHAPTER_RESULTS
        val itemsPerPage = result.feed?.itemsPerPage?.t?.toIntOrNull() ?: CHAPTER_CHUNK

        allEntries += result.feed?.entry ?: throw Exception("Failed to parse from chapter API")

        coroutineScope {
            (allEntries.size until totalResults step itemsPerPage).map { startIndex ->
                async {
                    fetchChapter(feedUrl, startIndex + 1, maxResults = itemsPerPage)
                        .feed!!.entry!!
                }
            }.awaitAll().flatten().also(allEntries::addAll)
        }

        return allEntries
            .filter { it.category.orEmpty().any { cat -> cat.term == chapterCategory } }
            .map { entry ->
                val updated = entry.getUpdatedDate()
                val published = entry.getPublishedDate()

                val dateStr = if (preferChapterUpdatedDate) {
                    updated ?: published
                } else {
                    published ?: updated
                }

                entry.toSChapter(baseUrl, parseDate(dateStr))
            }
    }

    protected open val supportsChapterFeed = true

    protected open val useNewChapterFeed = false
    protected open val useOldChapterFeed = false

    protected open val chapterFeedRegex = """clwd\.run\(["'](.*?)["']\)""".toRegex()
    protected open val scriptSelector = "#clwd > script"

    open fun getChapterFeedUrl(doc: Document, mangaTitle: String): String {
        if (useNewChapterFeed) return newChapterFeedUrl(doc)
        if (useOldChapterFeed) return oldChapterFeedUrl(doc)

        val feed = runCatching {
            val script = doc.selectFirst(scriptSelector)
                ?: return runCatching { oldChapterFeedUrl(doc) }
                    .getOrElse { newChapterFeedUrl(doc) }

            chapterFeedRegex
                .find(script.html())
                ?.groupValues?.get(1)
                ?: throw Exception("Failed to find chapter feed")
        }.getOrElse { mangaTitle }

        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .build().toString()
    }

    private val oldChapterFeedRegex = """([^']+)\?""".toRegex()
    private val oldScriptSelector = "#myUL > script"

    open fun oldChapterFeedUrl(doc: Document): String {
        val script = doc.selectFirst(oldScriptSelector)!!.attr("src")
        val feed = oldChapterFeedRegex
            .find(script)
            ?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter feed")

        return "$baseUrl$feed?alt=json"
    }

    private val newChapterFeedRegex = """label\s*=\s*'([^']+)'""".toRegex()
    private val newScriptSelector = "#latest > script"

    private fun newChapterFeedUrl(doc: Document): String {
        var chapterRegex = chapterFeedRegex
        var script = doc.selectFirst(scriptSelector)

        if (script == null) {
            script = doc.selectFirst(newScriptSelector)!!
            chapterRegex = newChapterFeedRegex
        }

        val feed = chapterRegex
            .find(script.html())
            ?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter feed")

        val url = apiUrl(feed)
            .addQueryParameter("start-index", "1")
            .addQueryParameter("max-results", "999999")
            .build()

        return url.toString()
    }

    // Pages
    protected open val pageListSelector = "div.check-box div.separator"

    override suspend fun getPageList(chapter: SChapter) = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

    open fun pageListParse(document: Document): List<Page> {
        val images = document.select(pageListSelector)
        return images.select("img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    // Filters
    protected open val hasFilters = false

    protected open val hasStatusFilter = true
    protected open val hasTypeFilter = true
    protected open val hasLanguageFilter = true
    protected open val hasGenreFilter = true

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterList = mutableListOf<Filter<*>>()

        if (!hasFilters) {
            return FilterList(emptyList())
        }

        filterList.add(Filter.Header(intl["filter_warning"]))
        filterList.add(Filter.Separator())

        if (hasStatusFilter) filterList.add(StatusList(intl["status_filter_title"], getStatusList()))
        if (hasTypeFilter) filterList.add(TypeList(intl["type_filter_title"], getTypeList()))
        if (hasLanguageFilter) filterList.add(LanguageList(intl["language_filter_title"], getLanguageList()))
        if (hasGenreFilter) filterList.add(GenreList(intl["genre_filter_title"], getGenreList()))

        return FilterList(filterList)
    }

    protected open fun getStatusList(): List<Status> = listOf(
        Status(intl["all"], ""),
        Status(intl["status_ongoing"], "Ongoing"),
        Status(intl["status_completed"], "Completed"),
        Status(intl["status_dropped"], "Dropped"),
        Status(intl["status_upcoming"], "Upcoming"),
        Status(intl["status_hiatus"], "Hiatus"),
        Status(intl["status_cancelled"], "Cancelled"),
    )

    protected open fun getTypeList(): List<Type> = listOf(
        Type(intl["all"], ""),
        Type(intl["type_manga"], "Manga"),
        Type(intl["type_manhua"], "Manhua"),
        Type(intl["type_manhwa"], "Manhwa"),
        Type(intl["type_novel"], "Novel"),
        Type(intl["type_web_novel_jp"], "Web Novel (JP)"),
        Type(intl["type_web_novel_kr"], "Web Novel (KR)"),
        Type(intl["type_web_novel_cn"], "Web Novel (CN)"),
        Type(intl["type_doujinshi"], "Doujinshi"),
    )

    protected open fun getGenreList() = listOf(
        "Action", "Adventure", "Comedy", "Crime",
        "Drama", "Ecchi", "Fantasy", "Harem",
        "Historical", "Horror", "Isekai", "Josei",
        "Magic", "Martial Arts", "Medical", "Military",
        "Music", "Mystery", "One Shot", "Police",
        "Psychological", "Reincarnation", "Revenge", "Romance",
        "School Life", "Sci-Fi", "Seinen", "Shounen",
        "Slice of Life", "Sports", "Supernatural", "Survival",
        "Thriller", "Time Travel", "Tragedy", "Vampire",
    ).map { Genre(it, it) }

    protected open fun getLanguageList(): List<Language> = listOf(
        Language(intl["all"], ""),
        Language("Indonesian", "Indonesian"),
        Language("English", "English"),
    )

    protected open val statusOnGoingList = listOf(
        "ongoing",
        "en curso",
        "en emisión",
        "em lançamento",
        "activo",
        "ativo",
        "lançando",
        "مستمر",
        "مستمرة",
    )

    protected open val statusCompletedList = listOf(
        "completed",
        "completo",
        "finalizado",
        "مكتمل",
        "مكتملة",
    )

    protected open val statusHiatusList = listOf(
        "hiatus",
        "pausado",
    )

    protected open val statusCancelledList = listOf(
        "cancelled",
        "dropped",
        "dropado",
        "abandonado",
        "cancelado",
    )

    // Utils

    private fun FeedUrl.isValid() = old == useOldChapterFeed &&
        new == useNewChapterFeed &&
        category == chapterCategory

    protected open fun parseDate(dateStr: String?) = dateFormat.tryParseDate(dateStr).takeIf { it > 0 } ?: Instant.tryParse(dateStr)

    protected open fun parseStatus(element: String): Int = when (element.lowercase().trim()) {
        in statusOnGoingList -> SManga.ONGOING
        in statusCompletedList -> SManga.COMPLETED
        in statusHiatusList -> SManga.ON_HIATUS
        in statusCancelledList -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String.replaceLast(oldValue: String, newValue: String): String {
        val lastIndexOf = lastIndexOf(oldValue)
        return if (lastIndexOf == -1) {
            this
        } else {
            substring(0, lastIndexOf) + newValue + substring(lastIndexOf + oldValue.length)
        }
    }

    companion object {
        private const val MAX_MANGA_RESULTS = 20
        private const val MAX_CHAPTER_RESULTS = 150
        private const val CHAPTER_CHUNK = 50
    }
}
