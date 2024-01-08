package eu.kanade.tachiyomi.extension.en.manhwasmen

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhwasMen : Madara("Manhwas Men", "https://manhwas.men", "en") {
    override val versionId = 2

    override val fetchGenres = false
    override val sendViewCount = false

    // popular
    override fun popularMangaSelector() = "div.col-6"
    override val popularMangaUrlSelector = ".series-box a"
    override fun popularMangaNextPageSelector() = "a[rel=next]"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga-list?page=$page", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return super.popularMangaFromElement(element).apply {
            title = element.select(popularMangaUrlSelector).text()
        }
    }

    // latest
    override fun latestUpdatesSelector() = "div.d-flex:nth-child(6) div.col-6"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
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
        return super.mangaDetailsParse(document).apply {
            document.select(mangaDetailsSelectorStatus).last()?.let {
                status = when (it.text()) {
                    in "complete" -> SManga.COMPLETED
                    in "ongoing" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    // chapter list
    override val chapterUrlSuffix = ""

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            name = element.select("p").text()
        }
    }

    // page list
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

    private class GenreFilter(title: String, genreList: Array<String>) :
        Filter.Select<String>(title, genreList)

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Note: Ignored if using text search"),
            GenreFilter("Genre", getGenreList),
        )
    }
}
