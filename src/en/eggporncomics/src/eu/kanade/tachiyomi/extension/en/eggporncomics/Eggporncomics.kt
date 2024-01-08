package eu.kanade.tachiyomi.extension.en.eggporncomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class Eggporncomics : ParsedHttpSource() {

    override val name = "Eggporncomics"

    override val baseUrl = "https://eggporncomics.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // couldn't find a page with popular comics, defaulting to the popular "anime-comics" category
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category/1/anime-comics?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.preview:has(div.name)"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a:has(img)").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
                thumbnail_url = it.select("img").attr("abs:src")
            }
        }
    }

    override fun popularMangaNextPageSelector() = "ul.ne-pe li.next:not(.disabled)"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-comics?page=$page", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    // when combining a category filter and comics filter, if there are no results the source
                    // issues a 404, override that so as not to confuse users
                    if (response.request.url.toString().contains("category-tag") && response.code == 404) {
                        Observable.just(MangasPage(emptyList(), false))
                    } else {
                        response.close()
                        throw Exception("HTTP error ${response.code}")
                    }
                }
            }
            .map { response ->
                searchMangaParse(response)
            }
    }

    private val queryRegex = Regex("""[\s']""")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search/${query.replace(queryRegex, "-")}?page=$page", headers)
        } else {
            val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
            val filterList = if (filters.isEmpty()) getFilterList() else filters
            val category = filterList.find { it is CategoryFilter } as UriPartFilter
            val comics = filterList.find { it is ComicsFilter } as UriPartFilter

            when {
                category.isNotNull() && comics.isNotNull() -> {
                    url.addPathSegments("category-tag/${category.toUriPart()}/${comics.toUriPart()}")
                }
                category.isNotNull() -> {
                    url.addPathSegments("category/${category.toUriPart()}")
                }
                comics.isNotNull() -> {
                    url.addPathSegments("comics-tag/${comics.toUriPart()}")
                }
            }

            url.addQueryParameter("page", page.toString())

            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    private val descriptionPrefixRegex = Regex(""":.*""")

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            thumbnail_url = document.select(pageListSelector).first()!!.toFullSizeImage()
            description = document.select("div.links ul").joinToString("\n") { element ->
                element.select("a")
                    .joinToString(prefix = element.select("span").text().replace(descriptionPrefixRegex, ": ")) { it.text() }
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = "Chapter"
                date_upload = response.asJsoup().select("div.info > div.meta li:contains(days ago)").firstOrNull()
                    ?.let { Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(it.text().substringBefore(" ").toIntOrNull() ?: 0)) }.timeInMillis }
                    ?: 0
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    private fun Element.toFullSizeImage() = this.attr("abs:src").replace("thumb300_", "")

    private val pageListSelector = "div.grid div.image img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListSelector).mapIndexed { i, img ->
            Page(i, "", img.toFullSizeImage())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Leave query blank to use filters"),
        Filter.Separator(),
        CategoryFilter("Category", getCategoryList),
        ComicsFilter("Comics", getComicsList),
    )

    private class CategoryFilter(name: String, vals: Array<Pair<String, String?>>) : UriPartFilter(name, vals)
    private class ComicsFilter(name: String, vals: Array<Pair<String, String?>>) : UriPartFilter(name, vals)

    private val getCategoryList: Array<Pair<String, String?>> = arrayOf(
        Pair("Any", null),
        Pair("3d comics", "7/3d-comics"),
        Pair("8muses", "18/8muses"),
        Pair("Anime", "1/anime"),
        Pair("Cartoon", "2/cartoon"),
        Pair("Dickgirls & Shemale", "6/dickgirls-shemale"),
        Pair("Furry", "4/furry"),
        Pair("Games comics", "3/games-comics"),
        Pair("Hentai manga", "10/hentai-manga"),
        Pair("Interracial", "14/interracial"),
        Pair("Milf", "11/milf"),
        Pair("Mindcontrol", "15/mindcontrol"),
        Pair("Porn Comix", "16/porn-comix"),
        Pair("Western", "12/western"),
        Pair("Yaoi/Gay", "8/yaoigay"),
        Pair("Yuri and Lesbian", "9/yuri-and-lesbian"),
    )

    private val getComicsList: Array<Pair<String, String?>> = arrayOf(
        Pair("Any", null),
        Pair("3d", "85/3d"),
        Pair("Adventure Time", "2950/adventure-time"),
        Pair("Anal", "13/anal"),
        Pair("Ben 10", "641/ben10"),
        Pair("Big boobs", "3025/big-boobs"),
        Pair("Big breasts", "6/big-breasts"),
        Pair("Big cock", "312/big-cock"),
        Pair("Bigass", "604/big-ass-porn-comics-new"),
        Pair("Black cock", "2990/black-cock"),
        Pair("Blowjob", "7/blowjob"),
        Pair("Bondage", "24/bondage"),
        Pair("Breast expansion hentai", "102/breast-expansion-new"),
        Pair("Cumshot", "427/cumshot"),
        Pair("Dark skin", "29/dark-skin"),
        Pair("Dofantasy", "1096/dofantasy"),
        Pair("Double penetration", "87/double-penetration"),
        Pair("Doujin moe", "3028/doujin-moe"),
        Pair("Erotic", "602/erotic"),
        Pair("Fairy tail porn", "3036/fairy-tail"),
        Pair("Fakku", "1712/Fakku-Comics-new"),
        Pair("Fakku comics", "1712/fakku-comics-new"),
        Pair("Family Guy porn", "774/family-guy"),
        Pair("Fansadox", "1129/fansadox-collection"),
        Pair("Feminization", "385/feminization"),
        Pair("Forced", "315/forced"),
        Pair("Full color", "349/full-color"),
        Pair("Furry", "19/furry"),
        Pair("Futanari", "2994/futanari"),
        Pair("Group", "58/group"),
        Pair("Hardcore", "304/hardcore"),
        Pair("Harry Potter porn", "338/harry-potter"),
        Pair("Hentai", "321/hentai"),
        Pair("Incest", "3007/incest"),
        Pair("Incest - Family Therapy Top", "3007/family-therapy-top"),
        Pair("Incognitymous", "545/incognitymous"),
        Pair("Interracical", "608/interracical"),
        Pair("Jab Comix", "1695/JAB-Comics-NEW-2"),
        Pair("Kaos comics", "467/kaos"),
        Pair("Kim Possible porn", "788/kim-possible"),
        Pair("Lesbian", "313/lesbian"),
        Pair("Locofuria", "343/locofuria"),
        Pair("Milf", "48/milf"),
        Pair("Milftoon", "1678/milftoon-comics"),
        Pair("Muscle", "2/muscle"),
        Pair("Nakadashi", "10/nakadashi"),
        Pair("PalComix", "373/palcomix"),
        Pair("Pokemon hentai", "657/pokemon"),
        Pair("Shadbase", "1717/shadbase-comics"),
        Pair("Shemale", "126/shemale"),
        Pair("Slut", "301/slut"),
        Pair("Sparrow hentai", "3035/sparrow-hentai"),
        Pair("Star Wars hentai", "1344/star-wars"),
        Pair("Stockings", "51/stockings"),
        Pair("Superheroine Central", "615/superheroine-central"),
        Pair("The Cummoner", "3034/the-cummoner"),
        Pair("The Rock Cocks", "3031/the-rock-cocks"),
        Pair("ZZZ Comics", "1718/zzz-comics"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String?>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
        fun isNotNull(): Boolean = toUriPart() != null
    }
}
