package eu.kanade.tachiyomi.extension.en.manhwasmen

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhwasMen : Madara("Manhwas Men", "https://manhwas.men", "en") {
    override val versionId = 2

    override val fetchGenres = false
    override val sendViewCount = false

    // popular
    override fun popularMangaSelector() = "ul > li > article.anime"
    override fun popularMangaNextPageSelector() = "div nav ul.pagination li.page-item.active + li a"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-list?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        title = element.selectFirst("h3.title")!!.text()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    // latest
    override fun latestUpdatesNextPageSelector() = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector())
            .map(::latestMangaFromElement).distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    private fun latestMangaFromElement(element: Element): SManga = popularMangaFromElement(element).apply {
        setUrlWithoutDomain(
            element.selectFirst("a")!!.attr("abs:href").replaceAfterLast("/", ""),
        )
    }

    // search
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder().apply {
            if (query.isEmpty()) {
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> {
                            addQueryParameter("genero", filter.values[filter.state])
                        }

                        else -> {}
                    }
                }
            } else {
                addQueryParameter("search", query)
            }
            addQueryParameter("page", "$page")
        }.build()

        return GET(url, headers)
    }

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val profileManga = document.selectFirst(".anime-single")!!
        return SManga.create().apply {
            title = profileManga.selectFirst(".title")!!.text()
            thumbnail_url = profileManga.selectFirst("img")!!.attr("abs:src")
            description = profileManga.selectFirst(".sinopsis")!!.text()
            status = parseStatus(profileManga.select("span.anime-type-peli").last()!!.text())
            genre = profileManga.select("p.genres > span").joinToString { it.text() }
            profileManga.selectFirst(altNameSelector)?.ownText()?.let {
                if (it.isNotBlank() && it.notUpdating()) {
                    description = when {
                        description.isNullOrBlank() -> "$altName $it"
                        else -> "${description}\n\n$altName $it"
                    }
                }
            }
        }
    }

    private fun parseStatus(status: String): Int = when (status) {
        "ongoing" -> SManga.ONGOING
        "complete" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override val altNameSelector = "div.container div aside h2.h5"

    // chapter list
    override fun chapterListSelector() = "aside.principal ul.episodes-list li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        name = element.selectFirst("a > div > p > span")!!.text()
        date_upload = parseRelativeDate(element.selectFirst("a > div > span")!!.text())
    }

    override val pageListParseSelector = "#chapter_imgs img"

    // genre
    private val getGenreList: Array<String> = arrayOf(
        "sub-english",
        "adult-drama-manhwa-mature",
        "drama",
        "rape",
        "revenge",
        "secret-relationship",
        "mature",
        "office",
        "harem",
        "romance",
        "ecchi",
        "noona",
        "hypnosis",
        "assistant",
        "special-ability",
        "awakening",
        "raws",
        "toomics",
        "1",
        "adult",
        "toptoon",
        "ntr",
        "bullying",
        "university",
        "comedy-romance-school-life-harem",
        "seinen",
        "sports",
        "virgin",
        "pingon-jaja",
        "lezhin",
        "school-life",
        "campus",
        "action-drama-fantasy-themes",
        "chef",
        "drama-fantasy-omniverse-romance",
        "vanilla",
        "comedy",
        "seniorjunior",
        "first-love",
        "saimin",
        "fantasy-harem",
        "raw",
        "romance-drama-harem",
        "fantasy",
        "18-adult-smut-manhwa-mature",
        "magic",
        "murin",
        "comedy-romance-school-life-drama-harem",
        "romance-drama-mature",
        "supernatural",
        "laezhin",
        "m",
        "romance-drama-harem-mature",
        "anytoon",
        "psychological",
        "militar",
        "chantaje",
        "four-sisters",
        "in-laws",
        "cheatinginfidelity-hypnosis-married-woman-netorare",
        "smut",
        "full-color",
        "comedia",
        "tragedia",
        "adult-romance-mature",
        "drama-harem-mature",
        "adult-drama-seinen-fantasy-harem",
        "adult-romance-drama-seinen-harem-mature",
        "action",
        "mystery",
        "thriller",
        "girlfriend",
        "collegestudent",
        "alumni",
        "lovetriangle",
        "parttimejob",
        "female-friend",
        "neighbour",
        "adult-romance-manhwa-mature",
        "married-woman",
        "beauty",
        "tomics",
        "adaptation-drama-romance",
        "succubus",
        "cosplay",
        "adult-romance-drama-harem",
        "humiliation",
        "two-girl",
        "craving",
        "aunt",
        "housekeeper",
        "manhwa",
        "sci-fi",
        "4-koma",
        "adult-manhwa-mature",
        "adult-romance-seinen",
        "netorare",
        "cohabitation-drama-ntr-office",
        "vida-universitaria",
        "vainilla",
        "comedy-romance-mature",
        "comedy-romance-drama-harem",
        "bodybuilding",
        "josei",
        "topstar",
        "naive-men",
        "mistery",
        "friend",
        "young-woman",
        "first-experience",
        "romance-drama-fantasy-slice-of-life-raw",
        "sisters",
        "slice-of-life",
        "dance",
        "romance-school-life-drama-mature",
        "drama-family",
        "universidad",
        "club",
        "bondage",
        "work-life",
        "romance-school-life-drama-harem",
        "big-pennis",
        "milf",
        "netori",
        "yuri",
        "adventure",
        "adult-romance-drama-smut-manhwa-mature",
        "wife",
        "temptation",
        "sexual-fantasy",
    )

    private class GenreFilter(title: String, genreList: Array<String>) : Filter.Select<String>(title, genreList)

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: Ignored if using text search"),
        GenreFilter("Genre", getGenreList),
    )
}
