package eu.kanade.tachiyomi.multisrc.zeistmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy

abstract class ZeistManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    protected val json: Json by injectLazy()

    private val intl by lazy { ZeistMangaIntl(lang) }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    protected open val popularMangaSelector = "div.PopularPosts div.grid > figure"
    protected open val popularMangaSelectorTitle = "figcaption > a"
    protected open val popularMangaSelectorUrl = "figcaption > a"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
                title = element.selectFirst(popularMangaSelectorTitle)!!.text()
                setUrlWithoutDomain(element.selectFirst(popularMangaSelectorUrl)!!.attr("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val startIndex = maxMangaResults * (page - 1) + 1
        val url = apiUrl()
            .addQueryParameter("orderby", "published")
            .addQueryParameter("max-results", (maxMangaResults + 1).toString())
            .addQueryParameter("start-index", startIndex.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val startIndex = maxMangaResults * (page - 1) + 1
        val url = apiUrl()
            .addQueryParameter("max-results", (maxMangaResults + 1).toString())
            .addQueryParameter("start-index", startIndex.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("q", query)
            val searchUrl = url.build().toString().replaceLast("q=", "q=label:$mangaCategory+")
            return GET(searchUrl, headers)
        }

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

        return GET(url.build(), headers)
    }

    protected open val excludedCategories: List<String> = listOf("Anime")

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonString = response.body.string()
        val result = json.decodeFromString<ZeistMangaDto>(jsonString)

        val mangas = result.feed?.entry.orEmpty()
            .filter { it.category.orEmpty().any { category -> category.term == mangaCategory } }
            .filterNot { it.category.orEmpty().any { category -> excludedCategories.contains(category.term) } }
            .map { it.toSManga(baseUrl) }

        val mangalist = mangas.toMutableList()
        if (mangas.size == maxMangaResults + 1) {
            mangalist.removeLast()
            return MangasPage(mangalist, true)
        }

        return MangasPage(mangalist, false)
    }

    protected open val statusSelectorList = listOf(
        "Status",
        "Estado",
    )

    protected open val authorSelectorList = listOf(
        "Author",
        "Autor",
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
    protected open val mangaDetailsSelectorDescription = "#synopsis"
    protected open val mangaDetailsSelectorGenres = "div.mt-15 > a[rel=tag]"
    protected open val mangaDetailsSelectorAuthor = "span#author"
    protected open val mangaDetailsSelectorArtist = "span#artist"
    protected open val mangaDetailsSelectorAltName = "header > p"
    protected open val mangaDetailsSelectorInfo = ".y6x11p"
    protected open val mangaDetailsSelectorInfoTitle = "strong"
    protected open val mangaDetailsSelectorInfoDescription = "span.dt"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val profileManga = document.selectFirst(mangaDetailsSelector)!!
        return SManga.create().apply {
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = buildString {
                append(profileManga.select(mangaDetailsSelectorDescription).text())
                append("\n\n")
                profileManga.selectFirst(mangaDetailsSelectorAltName)?.text()?.takeIf { it.isNotBlank() }?.let {
                    append("Alternative name(s): ")
                    append(it)
                }
            }.trim()
            genre = profileManga.select(mangaDetailsSelectorGenres)
                .joinToString { it.text() }
            author = profileManga.selectFirst(mangaDetailsSelectorAuthor)?.text()
            artist = profileManga.selectFirst(mangaDetailsSelectorArtist)?.text()

            val infoElement = profileManga.select(mangaDetailsSelectorInfo)
            infoElement.forEach { element ->
                val infoText = element.ownText().trim().ifEmpty { element.selectFirst(mangaDetailsSelectorInfoTitle)?.text()?.trim() ?: "" }
                val descText = element.select(mangaDetailsSelectorInfoDescription).text().trim()
                when {
                    statusSelectorList.any { infoText.contains(it) } -> {
                        status = parseStatus(descText)
                    }

                    authorSelectorList.any { infoText.contains(it) } -> {
                        author = descText
                    }

                    artisSelectorList.any { infoText.contains(it) } -> {
                        artist = descText
                    }
                }
            }
        }
    }

    protected open val chapterCategory: String = "Chapter"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val url = getChapterFeedUrl(document)
        val res = client.newCall(GET(url, headers)).execute()

        val result = json.decodeFromString<ZeistMangaDto>(res.body.string())
        return result.feed?.entry?.filter { it.category.orEmpty().any { category -> category.term == chapterCategory } }
            ?.map { it.toSChapter(baseUrl) }
            ?: throw Exception("Failed to parse from chapter API")
    }

    protected open val useNewChapterFeed = false
    protected open val useOldChapterFeed = false

    protected open val chapterFeedRegex = """clwd\.run\(["'](.*?)["']\)""".toRegex()
    protected open val scriptSelector = "#clwd > script"

    open fun getChapterFeedUrl(doc: Document): String {
        if (useNewChapterFeed) return newChapterFeedUrl(doc)
        if (useOldChapterFeed) return oldChapterFeedUrl(doc)

        val script = doc.selectFirst(scriptSelector)
            ?: return runCatching { oldChapterFeedUrl(doc) }
                .getOrElse { newChapterFeedUrl(doc) }

        val feed = chapterFeedRegex
            .find(script.html())
            ?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter feed")

        return apiUrl(chapterCategory)
            .addPathSegments(feed)
            .addQueryParameter("max-results", maxChapterResults.toString())
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

        return "$baseUrl$feed?alt=json&start-index=1&max-results=$maxChapterResults"
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

    protected open val pageListSelector = "div.check-box div.separator"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select(pageListSelector)
        return images.select("img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    protected open val mangaCategory: String = "Series"

    open fun apiUrl(feed: String = mangaCategory): HttpUrl.Builder {
        return "$baseUrl/feeds/posts/default/-/".toHttpUrl().newBuilder()
            .addPathSegment(feed)
            .addQueryParameter("alt", "json")
    }

    protected open val hasFilters = false

    protected open val hasStatusFilter = true
    protected open val hasTypeFilter = true
    protected open val hasLanguageFilter = true
    protected open val hasGenreFilter = true

    override fun getFilterList(): FilterList {
        val filterList = mutableListOf<Filter<*>>()

        if (!hasFilters) {
            return FilterList(emptyList())
        }

        filterList.add(Filter.Header(intl.filterWarning))
        filterList.add(Filter.Separator())

        if (hasStatusFilter) filterList.add(StatusList(intl.statusFilterTitle, getStatusList()))
        if (hasTypeFilter) filterList.add(TypeList(intl.typeFilterTitle, getTypeList()))
        if (hasLanguageFilter) filterList.add(LanguageList(intl.languageFilterTitle, getLanguageList()))
        if (hasGenreFilter) filterList.add(GenreList(intl.genreFilterTitle, getGenreList()))

        return FilterList(filterList)
    }

    protected open fun getStatusList(): List<Status> = listOf(
        Status(intl.all, ""),
        Status(intl.statusOngoing, "Ongoing"),
        Status(intl.statusCompleted, "Completed"),
        Status(intl.statusDropped, "Dropped"),
        Status(intl.statusUpcoming, "Upcoming"),
        Status(intl.statusHiatus, "Hiatus"),
        Status(intl.statusCancelled, "Cancelled"),
    )

    protected open fun getTypeList(): List<Type> = listOf(
        Type(intl.all, ""),
        Type(intl.typeManga, "Manga"),
        Type(intl.typeManhua, "Manhua"),
        Type(intl.typeManhwa, "Manhwa"),
        Type(intl.typeNovel, "Novel"),
        Type(intl.typeWebNovelJP, "Web Novel (JP)"),
        Type(intl.typeWebNovelKR, "Web Novel (KR)"),
        Type(intl.typeWebNovelCN, "Web Novel (CN)"),
        Type(intl.typeDoujinshi, "Doujinshi"),
    )

    protected open fun getGenreList(): List<Genre> = listOf(
        Genre("Action", "Action"),
        Genre("Adventurer", "Adventurer"),
        Genre("Comedy", "Comedy"),
        Genre("Dementia", "Dementia"),
        Genre("Drama", "Drama"),
        Genre("Ecchi", "Ecchi"),
        Genre("Fantasy", "Fantasy"),
        Genre("Game", "Game"),
        Genre("Harem", "Harem"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Josei", "Josei"),
        Genre("Magic", "Magic"),
        Genre("Martial Arts", "Martial Arts"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Music", "Music"),
        Genre("Mystery", "Mystery"),
        Genre("Parody", "Parody"),
        Genre("Police", "Police"),
        Genre("Psychological", "Psychological"),
        Genre("Romance", "Romance"),
        Genre("Samurai", "Samurai"),
        Genre("School", "School"),
        Genre("Sci-fi", "Sci-fi"),
        Genre("Seinen", "Seinen"),
        Genre("Shoujo", "Shoujo"),
        Genre("Shoujo Ai", "Shoujo Ai"),
        Genre("Shounen", "Shounen"),
        Genre("Slice of Life", "Slice of Life"),
        Genre("Space", "Space"),
        Genre("Sports", "Sports"),
        Genre("Super Power", "Super Power"),
        Genre("SuperNatural", "SuperNatural"),
        Genre("Thriller", "Thriller"),
        Genre("Vampire", "Vampire"),
        Genre("Work Life", "Work Life"),
        Genre("Yuri", "Yuri"),
    )

    protected open fun getLanguageList(): List<Language> = listOf(
        Language(intl.all, ""),
        Language("Indonesian", "Indonesian"),
        Language("English", "English"),
    )

    protected open val statusOnGoingList = listOf(
        "ongoing",
        "en curso",
        "en emisión",
        "activo",
        "ativo",
        "lançando",
        "مستمر",
    )

    protected open val statusCompletedList = listOf(
        "completed",
        "completo",
        "finalizado",
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
        private const val maxMangaResults = 20
        const val maxChapterResults = 999999
    }
}
