package eu.kanade.tachiyomi.extension.en.myhentaicomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MyHentaiComics : ParsedHttpSource() {

    override val name = "MyHentaiComics"

    override val baseUrl = "https://myhentaicomics.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient

    // Popular
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", FilterList())
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment("$page")
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        var url = baseUrl
        filters.forEach {
            when (it) {
                is GenreFilter -> url += it.toUriPart() + "/$page"
                else -> {}
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = "li.item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2").text()
            url = element.select("a").attr("href")
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaNextPageSelector() = "li.next a"

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val tags = document.selectFirst("div.comic-description")

        return SManga.create().apply {
            author = tags?.selectFirst("div:containsOwn(Artists) a")?.text()
            genre = tags?.select("div:containsOwn(Categories) a")?.joinToString { it.text() }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                },
            ),
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()
    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.comic-thumb > img").mapIndexed { i, e ->
            Page(i, imageUrl = e.attr("data-cfsrc").replace("/thumbnail/", "/original/"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("Cannot combine search types!"),
        Filter.Separator("-----------------"),
        GenreFilter(),
    )

    // [...document.querySelectorAll('.catagory-inner a')].map(a => `Pair("${a.querySelector("h2").textContent}", "${a.getAttribute('href')}")`).join(',\n')
    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("<Choose a genre>", ""),
            Pair("3D Comic", "/gallery/category/3"),
            Pair("Ahegao", "/gallery/category/23"),
            Pair("Anal", "/gallery/category/25"),
            Pair("Animated", "/gallery/category/10"),
            Pair("Asian", "/gallery/category/54"),
            Pair("Ass Expansion", "/gallery/category/5"),
            Pair("Aunt", "/gallery/category/6"),
            Pair("BBW", "/gallery/category/7"),
            Pair("Beastiality", "/gallery/category/8"),
            Pair("Bimbofication", "/gallery/category/2049"),
            Pair("Bisexual", "/gallery/category/9"),
            Pair("Black | Interracial", "/gallery/category/20"),
            Pair("Body Swap", "/gallery/category/11"),
            Pair("Bondage", "/gallery/category/12"),
            Pair("Breast Expansion", "/gallery/category/13"),
            Pair("Brother", "/gallery/category/1012"),
            Pair("Bukkake", "/gallery/category/15"),
            Pair("Catgirl", "/gallery/category/1201"),
            Pair("Cbt", "/gallery/category/8133"),
            Pair("Censored", "/gallery/category/5136"),
            Pair("Cheating", "/gallery/category/49"),
            Pair("Cosplay", "/gallery/category/8157"),
            Pair("Cousin", "/gallery/category/17"),
            Pair("Crossdressing", "/gallery/category/43"),
            Pair("Cuntboy", "/gallery/category/8134"),
            Pair("Dad | Father", "/gallery/category/788"),
            Pair("Daughter", "/gallery/category/546"),
            Pair("Dick Growth", "/gallery/category/21"),
            Pair("Double Penetration", "/gallery/category/8135"),
            Pair("Ebony", "/gallery/category/29"),
            Pair("Elf", "/gallery/category/1714"),
            Pair("Exhibitionism", "/gallery/category/1838"),
            Pair("Family", "/gallery/category/2094"),
            Pair("Femboy | Tomgirl | Sissy", "/gallery/category/8136"),
            Pair("Femdom", "/gallery/category/24"),
            Pair("Foot Fetish", "/gallery/category/1873"),
            Pair("Forced", "/gallery/category/18"),
            Pair("Furry", "/gallery/category/14"),
            Pair("Futanari | Shemale | Dickgirl", "/gallery/category/19"),
            Pair("Futanari X Female", "/gallery/category/1951"),
            Pair("Futanari X Futanari", "/gallery/category/1885"),
            Pair("Futanari X Male", "/gallery/category/26"),
            Pair("Gangbang", "/gallery/category/27"),
            Pair("Gay | Yaoi", "/gallery/category/28"),
            Pair("Gender Bender", "/gallery/category/16"),
            Pair("Giant", "/gallery/category/8137"),
            Pair("Giantess", "/gallery/category/452"),
            Pair("Gilf", "/gallery/category/8138"),
            Pair("Gloryhole", "/gallery/category/31"),
            Pair("Group", "/gallery/category/101"),
            Pair("Hairy Female", "/gallery/category/1986"),
            Pair("Hardcore", "/gallery/category/36"),
            Pair("Harem", "/gallery/category/53"),
            Pair("Inflation | Stomach Bulge", "/gallery/category/57"),
            Pair("Inseki", "/gallery/category/1978"),
            Pair("Kemonomimi", "/gallery/category/1875"),
            Pair("Lactation", "/gallery/category/39"),
            Pair("Lesbian | Yuri | Girls Only", "/gallery/category/41"),
            Pair("Milf", "/gallery/category/30"),
            Pair("Mind Break", "/gallery/category/2023"),
            Pair("Mind Control | Hypnosis", "/gallery/category/42"),
            Pair("Mom | Mother", "/gallery/category/56"),
            Pair("Monster", "/gallery/category/8140"),
            Pair("Monster Girl", "/gallery/category/8139"),
            Pair("Most Popular", "/gallery/category/52"),
            Pair("Muscle Girl", "/gallery/category/45"),
            Pair("Muscle Growth", "/gallery/category/46"),
            Pair("Nephew", "/gallery/category/47"),
            Pair("Niece", "/gallery/category/48"),
            Pair("Nipple Fuck | Nipple Penetration", "/gallery/category/8141"),
            Pair("Pegging", "/gallery/category/50"),
            Pair("Possession", "/gallery/category/51"),
            Pair("Pregnant | Impregnation", "/gallery/category/55"),
            Pair("Public Use", "/gallery/category/8142"),
            Pair("Selfcest", "/gallery/category/8143"),
            Pair("Sister", "/gallery/category/58"),
            Pair("Slave", "/gallery/category/8144"),
            Pair("Smegma", "/gallery/category/8145"),
            Pair("Solo", "/gallery/category/1865"),
            Pair("Solo Futa", "/gallery/category/8154"),
            Pair("Solo Girl", "/gallery/category/8146"),
            Pair("Solo Male", "/gallery/category/8147"),
            Pair("Son", "/gallery/category/62"),
            Pair("Spanking", "/gallery/category/38"),
            Pair("Speechless", "/gallery/category/8148"),
            Pair("Strap-On", "/gallery/category/61"),
            Pair("Stuck In Wall", "/gallery/category/8149"),
            Pair("Superheroes", "/gallery/category/59"),
            Pair("Tentacles", "/gallery/category/60"),
            Pair("Threesome", "/gallery/category/40"),
            Pair("Tickling", "/gallery/category/2065"),
            Pair("Titty Fuck | Paizuri", "/gallery/category/8150"),
            Pair("Tomboy", "/gallery/category/8153"),
            Pair("Transformation", "/gallery/category/37"),
            Pair("Uncle", "/gallery/category/63"),
            Pair("Urination", "/gallery/category/64"),
            Pair("Vanilla | Wholesome", "/gallery/category/8151"),
            Pair("Variant Set", "/gallery/category/8152"),
            Pair("Vore | Unbirth", "/gallery/category/65"),
            Pair("Weight Gain", "/gallery/category/66"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
