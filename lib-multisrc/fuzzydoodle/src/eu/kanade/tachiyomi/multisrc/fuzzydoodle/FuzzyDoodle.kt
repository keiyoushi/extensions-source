package eu.kanade.tachiyomi.multisrc.fuzzydoodle

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

/*
 * https://github.com/jhin1m/fuzzy-doodle
 */
abstract class FuzzyDoodle : KeiSource() {

    // Popular
    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get("$baseUrl/manga?page=$page").asJsoup())

    open fun popularMangaSelector() = "div#card-real"
    open fun popularMangaNextPageSelector() = "ul.pagination > li:last-child:not(.pagination-disabled)"

    protected fun popularMangaParse(document: Document): MangasPage {
        val entries = document.select(popularMangaSelector())
            .map(::popularMangaFromElement)
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    open fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("h2.text-sm")!!.text()
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    // latest
    protected open val latestFromHomePage = false

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (latestFromHomePage) latestHomePageUrl(page) else latestPageUrl(page)
        val document = client.get(url).asJsoup()
        val entries = document.select(latestUpdatesSelector())
            .map(::latestUpdatesFromElement)
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null

        return MangasPage(entries, hasNextPage)
    }

    protected open fun latestHomePageUrl(page: Int) = "$baseUrl/?page=$page"

    protected open fun latestPageUrl(page: Int) = "$baseUrl/latest?page=$page"

    open fun latestUpdatesSelector() = if (latestFromHomePage) {
        "section:has(h2:containsOwn(Recent Chapters)) div#card-real," +
            " section:has(h2:containsOwn(Chapitres récents)) div#card-real"
    } else {
        popularMangaSelector()
    }

    open fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    open fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    // search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("title", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return popularMangaParse(client.get(url).asJsoup())
    }

    open fun searchMangaSelector() = popularMangaSelector()
    open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    open fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "manga" || url.pathSegments.size < 2) return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    // filters
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = parseFilters(client.get("$baseUrl/manga").asJsoup()).toJsonElement()

    protected open fun parseFilters(document: Document) = FilterData(
        types = document.select("select[name=type] > option").map {
            it.ownText() to it.attr("value")
        },
        statuses = document.select("select[name=status] > option").map {
            it.ownText() to it.attr("value")
        },
        genres = document.select("div.grid > div.flex:has(> input[name=genre[]])").mapNotNull {
            val label = it.selectFirst("label")?.ownText()
                ?: return@mapNotNull null
            val value = it.selectFirst("input")?.attr("value")
                ?: return@mapNotNull null

            label to value
        },
    )

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList()
        val filters = mutableListOf<Filter<*>>()

        if (filterData.types.isNotEmpty()) {
            filters.add(TypeFilter(filterData.types))
        }
        if (filterData.statuses.isNotEmpty()) {
            filters.add(StatusFilter(filterData.statuses))
        }
        if (filterData.genres.isNotEmpty()) {
            filters.add(GenreFilter(filterData.genres))
        }

        return FilterList(filters)
    }

    // details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), fetchChapterList(document))
    }

    // details
    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
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
                        append(it)
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

    protected fun Element.getInfo(text: String): String? = selectFirst("p:has(span:containsOwn($text)) span.capitalize")
        ?.ownText()

    protected fun String?.removePlaceHolder(): String? = takeUnless { it == "-" }

    // chapters
    private suspend fun fetchChapterList(firstPage: Document): List<SChapter> {
        val originalUrl = firstPage.location()

        val chapterList = buildList {
            var page = 1
            var doc = firstPage
            while (true) {
                addAll(doc.select(chapterListSelector()).map(::chapterFromElement))
                if (doc.selectFirst(chapterListNextPageSelector()) == null) break
                page++
                doc = client.get("$originalUrl?page=$page").asJsoup()
            }
        }

        return chapterList
    }

    open fun chapterListSelector() = "div#chapters-list > a[href]"
    protected fun chapterListNextPageSelector() = latestUpdatesNextPageSelector()

    open fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst("#item-title, span")!!.ownText()
        date_upload = element.selectFirst("span.text-gray-500")?.text().parseRelativeDate()
    }

    // from madara
    protected open fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val number = numberRegex.find(this)?.value?.toIntOrNull() ?: return 0L
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
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div#chapter-container > img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("srcset") -> attr("srcset").substringBefore(" ")
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        else -> absUrl("src")
    }

    companion object {
        private val numberRegex = Regex("""(\d+)""")
    }
}
