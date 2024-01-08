package eu.kanade.tachiyomi.extension.en.myhentaigallery

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MyHentaiGallery : ParsedHttpSource() {

    override val name = "MyHentaiGallery"

    override val baseUrl = "https://myhentaigallery.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/gallery/category/2/$page", headers)
    }

    override fun popularMangaSelector() = "div.comic-inner"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select("h2").text()
            setUrlWithoutDomain(element.select("a").attr("href"))
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "li.next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/gallery/$page")

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/gallery/thumbnails/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/gallery/thumbnails/$id"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search/$page?query=$query", headers)
        } else {
            val url = "$baseUrl/gallery/category".toHttpUrl().newBuilder()

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url.addPathSegment(filter.toUriPart())
                    }
                    else -> {}
                }
            }
            url.addPathSegment("$page")

            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return document.select("div.comic-header").let { info ->
            SManga.create().apply {
                title = info.select("h1").text()
                genre = info.select("div:containsOwn(categories) a").joinToString { it.text() }
                artist = info.select("div:containsOwn(artists) a").text()
                thumbnail_url = document.selectFirst(".comic-listing .comic-inner img")?.attr("src")
                description = info.select("div:containsOwn(groups) a").let { groups ->
                    if (groups.isNotEmpty()) "Groups: ${groups.joinToString { it.text() }}\n" else ""
                }
                description += info.select("div:containsOwn(parodies) a").let { groups ->
                    if (groups.isNotEmpty()) "Parodies: ${groups.joinToString { it.text() }}" else ""
                }
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = response.request.url.toString().substringAfter(baseUrl)
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.comic-thumb img[src]").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src").replace("/thumbnail/", "/original/"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("<select>", "---"),
            Pair("3D Comic", "3"),
            Pair("Ahegao", "2740"),
            Pair("Anal", "2741"),
            Pair("Asian", "4"),
            Pair("Ass Expansion", "5"),
            Pair("Aunt", "6"),
            Pair("BBW", "7"),
            Pair("Beastiality", "8"),
            Pair("Bimbofication", "3430"),
            Pair("Bisexual", "9"),
            Pair("Black & Interracial", "10"),
            Pair("Body Swap", "11"),
            Pair("Bondage", "12"),
            Pair("Breast Expansion", "13"),
            Pair("Brother", "14"),
            Pair("Bukakke", "15"),
            Pair("Catgirl", "2742"),
            Pair("Cheating", "16"),
            Pair("Cousin", "17"),
            Pair("Crossdressing", "18"),
            Pair("Dad", "19"),
            Pair("Daughter", "20"),
            Pair("Dick Growth", "21"),
            Pair("Ebony", "3533"),
            Pair("Elf", "2744"),
            Pair("Exhibitionism", "2745"),
            Pair("Father", "22"),
            Pair("Femdom", "23"),
            Pair("Foot Fetish", "3253"),
            Pair("Furry", "24"),
            Pair("Futanari & Shemale & Dickgirl", "25"),
            Pair("Futanari X Female", "3416"),
            Pair("Futanari X Futanari", "3415"),
            Pair("Futanari X Male", "26"),
            Pair("Gangbang", "27"),
            Pair("Gay & Yaoi", "28"),
            Pair("Gender Bending", "29"),
            Pair("Giantess", "30"),
            Pair("Gloryhole", "31"),
            Pair("Hairy Female", "3418"),
            Pair("Hardcore", "36"),
            Pair("Harem", "37"),
            Pair("Incest", "38"),
            Pair("Inseki", "3417"),
            Pair("Kemonomimi", "3368"),
            Pair("Lactation", "39"),
            Pair("Lesbian & Yuri & Girls Only", "40"),
            Pair("Milf", "41"),
            Pair("Mind Break", "3419"),
            Pair("Mind Control & Hypnosis", "42"),
            Pair("Mom", "43"),
            Pair("Mother", "44"),
            Pair("Muscle Girl", "45"),
            Pair("Muscle Growth", "46"),
            Pair("Nephew", "47"),
            Pair("Niece", "48"),
            Pair("Orgy", "49"),
            Pair("Pegging", "50"),
            Pair("Possession", "51"),
            Pair("Pregnant & Impregnation", "52"),
            Pair("Rape", "53"),
            Pair("Sister", "54"),
            Pair("Solo", "2746"),
            Pair("Son", "55"),
            Pair("Spanking", "56"),
            Pair("Stomach Bulge", "57"),
            Pair("Strap-On", "58"),
            Pair("Superheroes", "59"),
            Pair("Tentacles", "60"),
            Pair("Threesome", "61"),
            Pair("Transformation", "62"),
            Pair("Uncle", "63"),
            Pair("Urination", "64"),
            Pair("Vore", "65"),
            Pair("Weight Gain", "66"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
