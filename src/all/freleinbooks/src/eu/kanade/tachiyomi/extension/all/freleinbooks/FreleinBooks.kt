package eu.kanade.tachiyomi.extension.all.freleinbooks

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FreleinBooks() : ParsedHttpSource() {
    override val baseUrl = "https://books.frelein.my.id"
    override val lang = "all"
    override val name = "Frelein Books"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val Element.imgSrc: String
        get() = attr("data-lazy-src")
            .ifEmpty { attr("data-src") }
            .ifEmpty { attr("src") }

    // Latest
    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")!!.imgSrc
        manga.title = element.select(".postTitle").text()
        manga.setUrlWithoutDomain(element.select(".postTitle > a").attr("abs:href"))
        return manga
    }

    override fun latestUpdatesNextPageSelector() = ".olderLink"
    override fun latestUpdatesRequest(page: Int): Request {
        return if (page == 1) {
            GET(baseUrl)
        } else {
            val dateParam = page * 7 * 2
            // Calendar set to the current date
            val calendar: Calendar = Calendar.getInstance()
            // rollback 14 days
            calendar.add(Calendar.DAY_OF_YEAR, -dateParam)
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            // now the date is 14 days back
            GET("$baseUrl/search?updated-max=${formatter.format(calendar.time)}T12:38:00%2B07:00&max-results=12&start=12&by-date=false")
        }
    }

    override fun latestUpdatesSelector() = ".blogPosts > article"

    // Popular
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.selectFirst("img")!!.imgSrc
        manga.title = element.select("h3").text()
        manga.setUrlWithoutDomain(element.select("h3 > a").attr("abs:href"))
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    override fun popularMangaSelector() = ".itemPopulars article"

    // Search
    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)
    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val tagFilter = filterList.findInstance<TagFilter>()!!
        val groupFilter = filterList.findInstance<GroupFilter>()!!
        val magazineFilter = filterList.findInstance<MagazineFilter>()!!
        val fashionMagazineFilter = filterList.findInstance<FashionMagazineFilter>()!!
        return when {
            query.isEmpty() && groupFilter.state != 0 -> GET("$baseUrl/search/label/${groupFilter.toUriPart()}")
            query.isEmpty() && magazineFilter.state != 0 -> GET("$baseUrl/search/label/${magazineFilter.toUriPart()}")
            query.isEmpty() && fashionMagazineFilter.state != 0 -> GET("$baseUrl/search/label/${fashionMagazineFilter.toUriPart()}")
            query.isEmpty() && tagFilter.state.isNotEmpty() -> GET("$baseUrl/search/label/${tagFilter.state}")
            query.isNotEmpty() -> GET("$baseUrl/search?q=$query")
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".postTitle").text()
        manga.description = "Read ${document.select(".postTitle").text()} \n \nNote: If you encounters error when opening the magazine, please press the WebView button then leave a comment on our web so we can update it soon."
        manga.genre = document.select(".labelLink > a")
            .joinToString(", ") { it.text() }
        manga.status = SManga.COMPLETED
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("link[rel=\"canonical\"]").attr("href"))
        chapter.name = "Gallery"
        chapter.date_upload = getDate(element.select("link[rel=\"canonical\"]").attr("href"))
        return chapter
    }

    override fun chapterListSelector() = "html"

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("noscript").remove()
        document.select(".gallerybox a > img").forEachIndexed { i, it ->
            // format new img/b/
            if (it.imgSrc.contains("img/b/")) {
                if (it.imgSrc.contains("/w768-rw/")) {
                    val itUrl = it.imgSrc.replace("/w768-rw/", "/s0/")
                    pages.add(Page(i, itUrl, itUrl))
                }
                if (it.imgSrc.contains("/w480-rw/")) {
                    val itUrl = it.imgSrc.replace("/w480-rw/", "/s0/")
                    pages.add(Page(i, itUrl, itUrl))
                }
            }
            // format new img/b/
            else {
                if (it.imgSrc.contains("=w768-rw")) {
                    val itUrl = it.imgSrc.replace("=w768-rw", "")
                    pages.add(Page(i, itUrl, itUrl))
                } else if (it.imgSrc.contains("=w480-rw")) {
                    val itUrl = it.imgSrc.replace("=w480-rw", "")
                    pages.add(Page(i, itUrl, itUrl))
                } else {
                    val itUrl = it.imgSrc
                    pages.add(Page(i, itUrl, itUrl))
                }
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one filter will be applied!"),
        Filter.Separator(),
        GroupFilter(),
        MagazineFilter(),
        FashionMagazineFilter(),
        TagFilter(),
    )

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class MagazineFilter : UriPartFilter(
        "Magazine",
        arrayOf(
            Pair("Any", ""),
            Pair("B.L.T.", "B.L.T."),
            Pair("BIG ONE GIRLS", "BIG ONE GIRLS"),
            Pair("BOMB!", "BOMB!"),
            Pair("BRODY", "BRODY"),
            Pair("BUBKA", "BUBKA"),
            Pair("ENTAME", "ENTAME"),
            Pair("EX Taishu", "EX Taishu"),
            Pair("FINEBOYS", "FINEBOYS"),
            Pair("FLASH", "FLASH"),
            Pair("Fine", "Fine"),
            Pair("Friday", "Friday"),
            Pair("HINA_SATSU", "HINA_SATSU"),
            Pair("IDOL AND READ", "IDOL AND READ"),
            Pair("Kadokawa Scene 07", "Kadokawa Scene 07"),
            Pair("Monthly Basketball", "Monthly Basketball"),
            Pair("Monthly Young Magazine", "Monthly Young Magazine"),
            Pair("NOGI_SATSU", "NOGI_SATSU"),
            Pair("Nylon Japan", "Nylon Japan"),
            Pair("Platinum FLASH", "Platinum FLASH"),
            Pair("Shonen Magazine", "Shonen Magazine"),
            Pair("Shukan Post", "Shukan Post"),
            Pair("TOKYO NEWS MOOK", "TOKYO NEWS MOOK"),
            Pair("TV LIFE,Tarzan", "TV LIFE,Tarzan"),
            Pair("Tokyo Calendar", "Tokyo Calendar"),
            Pair("Top Yell NEO", "Top Yell NEO"),
            Pair("UTB", "UTB"),
            Pair("Weekly Playboy", "Weekly Playboy"),
            Pair("Weekly SPA", "Weekly SPA"),
            Pair("Weekly SPA!", "Weekly SPA!"),
            Pair("Weekly Shonen Champion", "Weekly Shonen Champion"),
            Pair("Weekly Shonen Magazine", "Weekly Shonen Magazine"),
            Pair("Weekly Shonen Sunday", "Weekly Shonen Sunday"),
            Pair("Weekly Shounen Magazine", "Weekly Shounen Magazine"),
            Pair("Weekly The Television Plus", "Weekly The Television Plus"),
            Pair("Weekly Zero Jump", "Weekly Zero Jump"),
            Pair("Yanmaga Web", "Yanmaga Web"),
            Pair("Young Animal", "Young Animal"),
            Pair("Young Champion", "Young Champion"),
            Pair("Young Gangan", "Young Gangan"),
            Pair("Young Jump", "Young Jump"),
            Pair("Young Magazine", "Young Magazine"),
            Pair("blt graph.", "blt graph."),
            Pair("mini", "mini"),
        ),
    )

    class FashionMagazineFilter : UriPartFilter(
        "Fashion Magazine",
        arrayOf(
            Pair("Any", ""),
            Pair("BAILA", "BAILA"),
            Pair("Biteki", "Biteki"),
            Pair("CLASSY", "CLASSY"),
            Pair("CanCam", "CanCam"),
            Pair("JJ", "JJ"),
            Pair("LARME", "LARME"),
            Pair("MARQUEE", "MARQUEE"),
            Pair("Maquia", "Maquia"),
            Pair("Men's non-no", "Men's non-no"),
            Pair("More", "More"),
            Pair("Oggi", "Oggi"),
            Pair("Ray", "Ray"),
            Pair("Seventeen", "Seventeen"),
            Pair("Sweet", "Sweet"),
            Pair("VOCE", "VOCE"),
            Pair("ViVi", "ViVi"),
            Pair("With", "With"),
            Pair("aR", "aR"),
            Pair("anan", "anan"),
            Pair("bis", "bis"),
            Pair("non-no", "non-no"),
        ),
    )

    class GroupFilter : UriPartFilter(
        "Group",
        arrayOf(
            Pair("Any", ""),
            Pair("Hinatazaka46", "Hinatazaka46"),
            Pair("Nogizaka46", "Nogizaka46"),
            Pair("Sakurazaka46", "Sakurazaka46"),
            Pair("Keyakizaka46", "Keyakizaka46"),
        ),
    )

    class TagFilter : Filter.Text("Tag")

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun getDate(str: String): Long {
        val regex = "[0-9]{4}\\/[0-9]{2}\\/[0-9]{2}".toRegex()
        val match = regex.find(str)
        return runCatching { DATE_FORMAT.parse(match!!.value)?.time }.getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMAT by lazy {
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        }
    }
}
