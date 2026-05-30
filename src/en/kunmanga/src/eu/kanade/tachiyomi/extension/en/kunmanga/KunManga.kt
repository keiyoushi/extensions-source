package eu.kanade.tachiyomi.extension.en.kunmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class KunManga :
    Madara(
        "Kun Manga",
        "https://www.kunmanga.online",
        "en",
    ) {

    override val client = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page > 1) {
            "$baseUrl/page/$page/?orderby=views&post_type=wp-manga"
        } else {
            "$baseUrl/?orderby=views&post_type=wp-manga"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaCards(response)

    // Recent

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("action", "madara_load_more")
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("template", "madara-core/content/content-archive")
            addQueryParameter("vars[orderby]", "meta_value_num")
            addQueryParameter("vars[paged]", page.toString())
            addQueryParameter("vars[timerange]", "")
            addQueryParameter("vars[posts_per_page]", "20")
            addQueryParameter("vars[tax_query][relation]", "OR")
            addQueryParameter("vars[meta_query][0][relation]", "AND")
            addQueryParameter("vars[meta_query][relation]", "AND")
            addQueryParameter("vars[post_type]", "wp-manga")
            addQueryParameter("vars[post_status]", "publish")
            addQueryParameter("vars[meta_key]", "_latest_update")
            addQueryParameter("vars[order]", "desc")
            addQueryParameter("vars[sidebar]", "right")
            addQueryParameter("vars[manga_archives_item_layout]", "big_thumbnail")
            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaCards(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = (if (page > 1) "$baseUrl/page/$page/" else baseUrl).toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            addQueryParameter("post_type", "wp-manga")

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> if (filter.state.isNotEmpty()) addQueryParameter("author", filter.state)
                    is ArtistFilter -> if (filter.state.isNotEmpty()) addQueryParameter("artist", filter.state)
                    is YearFilter -> if (filter.state.isNotEmpty()) addQueryParameter("release", filter.state)

                    is OperatorFilter -> addQueryParameter("op", filter.selectedValue())
                    is AdultFilter -> {
                        val adult = filter.selectedValue()
                        if (adult.isNotEmpty()) addQueryParameter("adult", adult)
                    }
                    is OrderByFilter -> {
                        val order = filter.selectedValue()
                        if (order.isNotEmpty()) addQueryParameter("orderby", order)
                    }

                    is GenreListFilter -> filter.state.filter { it.state }.forEach { addQueryParameter("genre[]", it.value) }
                    is StatusListFilter -> filter.state.filter { it.state }.forEach { addQueryParameter("status[]", it.value) }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaCards(response)

    // Shared parsing

    private fun parseMangaCards(response: Response): MangasPage {
        val document = response.asJsoup()
        val baseHost = baseUrl.toHttpUrl().host

        val mangas = document.select(".c-tabs-item__content, .page-item-detail").mapNotNull { element ->
            val titleEl = element.selectFirst(".post-title a, h3.h4 a") ?: return@mapNotNull null

            SManga.create().apply {
                title = titleEl.text()
                setUrlWithoutDomain(titleEl.absUrl("href"))

                thumbnail_url = element.selectFirst("img")?.let { img ->
                    listOf("data-backup", "src", "data-src", "data-lazy-src", "data-aload")
                        .map { img.absUrl(it) }
                        .firstOrNull { it.startsWith("http") && !it.contains("$baseHost/thumb") }
                }
            }
        }

        val isAjax = response.request.url.queryParameter("action") == "madara_load_more"

        val hasNextPage = if (isAjax) {
            mangas.size >= 20
        } else {
            document.selectFirst("a[aria-label=Next], .nav-previous, .next.page-numbers, .pagination-next, .wp-pagenavi .next, a[rel=next], .next") != null
        }

        return MangasPage(mangas, hasNextPage)
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): rx.Observable<List<SChapter>> = rx.Observable.fromCallable {
        val allChapters = mutableListOf<SChapter>()

        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments.getOrNull(1) ?: return@fromCallable emptyList()

        var currentPage = 1
        var lastPage = 1

        do {
            val apiUrl = "$baseUrl/api/comics/$slug/chapters?page=$currentPage&per_page=50&order=desc"
            val response = client.newCall(GET(apiUrl, apiHeaders)).execute()
            val data = response.parseAs<ChapterListResponse>().data ?: break

            lastPage = data.lastPage
            data.chapters.forEach { chapter ->
                allChapters.add(chapter.toSChapter(slug) { apiDateFormat.tryParse(it?.substringBefore(".")) })
            }

            currentPage++
        } while (currentPage <= lastPage)

        allChapters
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        AuthorFilter(),
        ArtistFilter(),
        YearFilter(),

        OperatorFilter(),

        AdultFilter(),
        OrderByFilter(),

        Filter.Separator(),

        GenreListFilter(getGenreList()),
        StatusListFilter(getStatusList()),
    )

    // Private

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "application/json")
            .build()
    }

    private val imageHeaders = headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .build()

    private class AuthorFilter : Filter.Text("Author")
    private class ArtistFilter : Filter.Text("Artist")
    private class YearFilter : Filter.Text("Release Year")

    private class OperatorFilter : Filter.Select<String>("Genre Operator", arrayOf("OR", "AND")) {
        fun selectedValue() = if (state == 0) "" else "1"
    }

    private class AdultFilter : Filter.Select<String>("Adult Content", arrayOf("All", "Safe (18-)", "Adult (18+)")) {
        fun selectedValue() = when (state) {
            1 -> "0"
            2 -> "1"
            else -> ""
        }
    }

    private class OrderByFilter : Filter.Select<String>("Order By", arrayOf("Relevance", "Latest", "A-Z", "Rating", "Trending", "Most Views", "New Manga")) {
        fun selectedValue() = when (state) {
            1 -> "latest"
            2 -> "alphabet"
            3 -> "rating"
            4 -> "trending"
            5 -> "views"
            6 -> "new-manga"
            else -> ""
        }
    }

    private class Status(name: String, val value: String) : Filter.CheckBox(name)
    private class StatusListFilter(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    private fun getStatusList() = listOf(
        Status("On-Going", "ongoing"),
        Status("Completed", "completed"),
        Status("On-Hold", "on-hold"),
        Status("Dropped", "drop"),
    )

    private class Genre(name: String, val value: String) : Filter.CheckBox(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adaptation", "adaptation"),
        Genre("Adventure", "adventure"),
        Genre("All Ages", "all-ages"),
        Genre("Bloody", "bloody"),
        Genre("Boys Love", "boys-love"),
        Genre("Comedy", "comedy"),
        Genre("Comic", "comic"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Cultivation", "cultivation"),
        Genre("Demon", "demon"),
        Genre("Drama", "drama"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Girls Love", "girls-love"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Monster", "monster"),
        Genre("Mystery", "mystery"),
        Genre("Office Workers", "office-workers"),
        Genre("Oneshot", "oneshot"),
        Genre("Psychological", "psychological"),
        Genre("Rebirth", "rebirth"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Video Games", "video-games"),
        Genre("Webtoon", "webtoon"),
        Genre("Wuxia", "wuxia"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
