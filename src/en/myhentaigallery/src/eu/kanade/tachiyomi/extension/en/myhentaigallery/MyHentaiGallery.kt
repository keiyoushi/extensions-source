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
        return GET("$baseUrl/views/$page", headers)
    }

    override fun popularMangaSelector() = "div.comic-inner"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("h2")!!.text()
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            thumbnail_url = element.selectFirst("img")?.absUrl("src")
        }
    }

    override fun popularMangaNextPageSelector() = "li.next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/gpage/$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/g/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/g/$id"
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
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addPathSegment(page.toString())
                .addQueryParameter("query", query)
                .build()
            GET(url, headers)
        } else {
            val url = "$baseUrl/g/category".toHttpUrl().newBuilder()

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        url.addPathSegment(filter.toUriPart())
                    }
                    else -> {}
                }
            }
            url.addPathSegment("$page")

            GET(url.build(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return document.selectFirst("div.comic-header")!!.let { info ->
            SManga.create().apply {
                title = info.selectFirst("h1")!!.text()
                genre = info.select("div:containsOwn(categories) a").joinToString { it.text() }
                artist = info.select("div:containsOwn(artists) a").joinToString { it.text() }
                thumbnail_url = document.selectFirst(".comic-listing .comic-inner img")?.absUrl("src")
                description = buildString {
                    info.select("div:containsOwn(groups) a")
                        .takeIf { it.isNotEmpty() }
                        ?.also { if (isNotEmpty()) append("\n\n") }
                        ?.also { appendLine("Groups:") }
                        ?.joinToString("\n") { "- ${it.text()}" }
                        ?.also { append(it) }

                    info.select("div:containsOwn(parodies) a")
                        .takeIf { it.isNotEmpty() }
                        ?.also { if (isNotEmpty()) append("\n\n") }
                        ?.also { appendLine("Parodies:") }
                        ?.joinToString("\n") { "- ${it.text()}" }
                        ?.also { append(it) }
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

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.comic-thumb img[src]").mapIndexed { i, img ->
            val imageUrl = img.absUrl("src").replace("/thumbnail/", "/original/")
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Category",
        arrayOf(
            // copy($$(".catagory-inner > a").map(el => {var id = /\d+$/.exec(el.href.replace())[0]; var name = el.querySelector("h2").innerText; return `Pair("${name}", "${id}"),`;}).join("\n"))
            Pair("<select>", "---"),
            Pair("3D Comic", "3"),
            Pair("Ahegao", "23"),
            Pair("Anal", "25"),
            Pair("Animated", "10"),
            Pair("Asian", "54"),
            Pair("Ass Expansion", "5"),
            Pair("Aunt", "6"),
            Pair("BBW", "7"),
            Pair("Beastiality", "8"),
            Pair("Bimbofication", "2049"),
            Pair("Bisexual", "9"),
            Pair("Black | Interracial", "20"),
            Pair("Body Swap", "11"),
            Pair("Bondage", "12"),
            Pair("Breast Expansion", "13"),
            Pair("Brother", "1012"),
            Pair("Bukkake", "15"),
            Pair("Catgirl", "1201"),
            Pair("Cbt", "8133"),
            Pair("Censored", "5136"),
            Pair("Cheating", "49"),
            Pair("Cosplay", "8157"),
            Pair("Cousin", "17"),
            Pair("Crossdressing", "43"),
            Pair("Cuntboy", "8134"),
            Pair("Dad | Father", "788"),
            Pair("Daughter", "546"),
            Pair("Dick Growth", "21"),
            Pair("Double Penetration", "8135"),
            Pair("Ebony", "29"),
            Pair("Elf", "1714"),
            Pair("Exhibitionism", "1838"),
            Pair("Family", "2094"),
            Pair("Femboy | Tomgirl | Sissy", "8136"),
            Pair("Femdom", "24"),
            Pair("Foot Fetish", "1873"),
            Pair("Forced", "18"),
            Pair("Furry", "14"),
            Pair("Futanari | Shemale | Dickgirl", "19"),
            Pair("Futanari X Female", "1951"),
            Pair("Futanari X Futanari", "1885"),
            Pair("Futanari X Male", "26"),
            Pair("Gangbang", "27"),
            Pair("Gay | Yaoi", "28"),
            Pair("Gender Bender", "16"),
            Pair("Giant", "8137"),
            Pair("Giantess", "452"),
            Pair("Gilf", "8138"),
            Pair("Gloryhole", "31"),
            Pair("Group", "101"),
            Pair("Hairy Female", "1986"),
            Pair("Hardcore", "36"),
            Pair("Harem", "53"),
            Pair("Inflation | Stomach Bulge", "57"),
            Pair("Inseki", "1978"),
            Pair("Kemonomimi", "1875"),
            Pair("Lactation", "39"),
            Pair("Lesbian | Yuri | Girls Only", "41"),
            Pair("Milf", "30"),
            Pair("Mind Break", "2023"),
            Pair("Mind Control | Hypnosis", "42"),
            Pair("Mom | Mother", "56"),
            Pair("Monster", "8140"),
            Pair("Monster Girl", "8139"),
            Pair("Most Popular", "52"),
            Pair("Muscle Girl", "45"),
            Pair("Muscle Growth", "46"),
            Pair("Nephew", "47"),
            Pair("Niece", "48"),
            Pair("Nipple Fuck | Nipple Penetration", "8141"),
            Pair("Pegging", "50"),
            Pair("Possession", "51"),
            Pair("Pregnant | Impregnation", "55"),
            Pair("Public Use", "8142"),
            Pair("Selfcest", "8143"),
            Pair("Sister", "58"),
            Pair("Slave", "8144"),
            Pair("Smegma", "8145"),
            Pair("Solo", "1865"),
            Pair("Solo Futa", "8154"),
            Pair("Solo Girl", "8146"),
            Pair("Solo Male", "8147"),
            Pair("Son", "62"),
            Pair("Spanking", "38"),
            Pair("Speechless", "8148"),
            Pair("Strap-On", "61"),
            Pair("Stuck In Wall", "8149"),
            Pair("Superheroes", "59"),
            Pair("Tentacles", "60"),
            Pair("Threesome", "40"),
            Pair("Tickling", "2065"),
            Pair("Titty Fuck | Paizuri", "8150"),
            Pair("Tomboy", "8153"),
            Pair("Transformation", "37"),
            Pair("Uncle", "63"),
            Pair("Urination", "64"),
            Pair("Vanilla | Wholesome", "8151"),
            Pair("Variant Set", "8152"),
            Pair("Vore | Unbirth", "65"),
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
