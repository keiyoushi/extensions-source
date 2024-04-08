package eu.kanade.tachiyomi.multisrc.fuzzydoodle

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.lang.Exception
import java.util.Calendar

/*
 * https://github.com/jhin1m/fuzzy-doodle
 */
abstract class FuzzyDoodle(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaSelector() = "div#card-real"
    override fun popularMangaNextPageSelector() = "ul.pagination > li:last-child:not(.pagination-disabled)"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        launchIO { fetchFilters(document) }

        val entries = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("h2.text-sm")!!.text()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    // latest
    protected open val latestFromHomePage = false

    override fun latestUpdatesRequest(page: Int) =
        if (latestFromHomePage) {
            latestHomePageRequest(page)
        } else {
            latestPageRequest(page)
        }

    protected open fun latestHomePageRequest(page: Int) =
        GET("$baseUrl/?page=$page", headers)

    protected open fun latestPageRequest(page: Int) =
        GET("$baseUrl/latest?page=$page", headers)

    override fun latestUpdatesSelector() =
        if (latestFromHomePage) {
            "section:has(h2:containsOwn(Recent Chapters)) div#card-real," +
                " section:has(h2:containsOwn(Chapitres récents)) div#card-real"
        } else {
            popularMangaSelector()
        }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesParse(response: Response): MangasPage {
        launchIO { fetchFilters() }

        return super.latestUpdatesParse(response)
    }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("title", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // filters
    protected var typeList = listOf<Pair<String, String>>()
    protected var statusList = listOf<Pair<String, String>>()
    protected var genreList = listOf<Pair<String, String>>()
    private var fetchFilterAttempts = 0

    protected suspend fun fetchFilters(document: Document? = null) {
        if (fetchFilterAttempts < 3 && (typeList.isEmpty() || statusList.isEmpty() || genreList.isEmpty())) {
            try {
                val doc = document ?: client.newCall(filtersRequest())
                    .await()
                    .asJsoup()

                parseFilters(doc)
            } catch (e: Exception) {
                Log.e("$name: Filters", e.stackTraceToString())
            }
            fetchFilterAttempts++
        }
    }

    protected open fun filtersRequest() = GET("$baseUrl/manga", headers)

    protected open fun parseFilters(document: Document) {
        typeList = document.select("select[name=type] > option").map {
            it.ownText() to it.attr("value")
        }
        statusList = document.select("select[name=status] > option").map {
            it.ownText() to it.attr("value")
        }
        genreList = document.select("div.grid > div.flex:has(> input[name=genre[]])").mapNotNull {
            val label = it.selectFirst("label")?.ownText()
                ?: return@mapNotNull null
            val value = it.selectFirst("input")?.attr("value")
                ?: return@mapNotNull null

            label to value
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        if (typeList.isNotEmpty()) {
            filters.add(TypeFilter(typeList))
        }
        if (statusList.isNotEmpty()) {
            filters.add(StatusFilter(statusList))
        }
        if (genreList.isNotEmpty()) {
            filters.add(GenreFilter(genreList))
        }
        if (filters.size < 3) {
            filters.add(0, Filter.Header("Press 'reset' to load more filters"))
        }

        return FilterList(filters)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    protected fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    // details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val genres = mutableListOf<String>()
        with(document.selectFirst("main > section > div")!!) {
            thumbnail_url = selectFirst("div.relative img")?.imgAttr()
            title = selectFirst("div.flex > h1, div.flex > h2")!!.ownText()
            genres.addAll(select("div.flex > a.inline-block").eachText())
            description = buildString {
                selectFirst("div:has(> p#description)")?.let {
                    it.selectFirst("span.font-semibold")?.remove()
                    it.select("#show-more").remove()
                    append(it.text())
                }
                selectFirst("div.flex > h1 + div > span.text-sm, div.flex > h2 + div > span.text-sm")?.text()?.let {
                    if (it.isNotEmpty()) {
                        append("\n\n")
                        append("Alternative Title: ")
                        append(it.trim())
                    }
                }
            }.trim()
        }
        document.selectFirst("div#buttons + div.hidden, div:has(> div#buttons) + div.flex")?.run {
            status = (getInfo("Status") ?: getInfo("Statut")).parseStatus()
            artist = (getInfo("Artist") ?: getInfo("المؤلف") ?: getInfo("Artiste")).removePlaceHolder()
            author = (getInfo("Author") ?: getInfo("الرسام") ?: getInfo("Auteur")).removePlaceHolder()
            (getInfo("Type") ?: getInfo("النوع"))?.also { genres.add(0, it) }
        }
        genre = genres.joinToString()
    }

    protected open fun String?.parseStatus(): Int {
        this ?: return SManga.UNKNOWN

        return when {
            listOf("ongoing", "مستمر", "en cours").any { contains(it, true) } -> SManga.ONGOING
            listOf("dropped", "cancelled", "متوقف").any { contains(it, true) } -> SManga.CANCELLED
            listOf("completed", "مكتمل", "terminé").any { contains(it, true) } -> SManga.COMPLETED
            listOf("hiatus").any { contains(it, true) } -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    protected fun Element.getInfo(text: String): String? =
        selectFirst("p:has(span:containsOwn($text)) span.capitalize")
            ?.ownText()
            ?.trim()

    protected fun String?.removePlaceHolder(): String? =
        takeUnless { it == "-" }

    // chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val originalUrl = response.request.url.toString()

        val chapterList = buildList {
            var page = 1
            do {
                val doc = when {
                    isEmpty() -> response // First page
                    else -> {
                        page++
                        client.newCall(GET("$originalUrl?page=$page", headers)).execute()
                    }
                }.asJsoup()

                addAll(doc.select(chapterListSelector()).map(::chapterFromElement))
            } while (doc.selectFirst(chapterListNextPageSelector()) != null)
        }

        return chapterList
    }

    override fun chapterListSelector() = "div#chapters-list > a[href]"
    protected fun chapterListNextPageSelector() = latestUpdatesNextPageSelector()

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("#item-title, span")!!.ownText()
        date_upload = element.selectFirst("span.text-gray-500")?.text().parseRelativeDate()
    }

    // from madara
    protected open fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val number = Regex("""(\d+)""").find(this)?.value?.toIntOrNull() ?: return 0L
        val cal = Calendar.getInstance()

        return when {
            listOf("detik", "segundo", "second", "วินาที").any { contains(it, true) } -> {
                cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            }
            listOf("menit", "dakika", "min", "minute", "minuto", "นาที", "دقائق").any { contains(it, true) } -> {
                cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            }
            listOf("jam", "saat", "heure", "hora", "hour", "ชั่วโมง", "giờ", "ore", "ساعة", "小时").any { contains(it, true) } -> {
                cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            }
            listOf("hari", "gün", "jour", "día", "dia", "day", "วัน", "ngày", "giorni", "أيام", "天").any { contains(it, true) } -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            }
            listOf("week", "sema").any { contains(it, true) } -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            }
            listOf("month", "mes").any { it in this } -> {
                cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            }
            listOf("year", "año").any { it in this } -> {
                cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            }
            else -> 0L
        }
    }

    // pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#chapter-container > img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("srcset") -> attr("srcset").substringBefore(" ")
            hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
            hasAttr("data-src") -> absUrl("data-src")
            hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
            else -> absUrl("src")
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }
}
