package eu.kanade.tachiyomi.extension.all.galaxy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.util.Calendar

abstract class Galaxy(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return if (page == 1) {
            GET("$baseUrl/webtoons/romance/home", headers)
        } else {
            GET("$baseUrl/webtoons/action/home", headers)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(
            """div.tabs div[wire:snapshot*=App\\Models\\Serie], main div:has(h2:matches(Today\'s Hot|الرائج اليوم)) a[wire:snapshot*=App\\Models\\Serie]""",
        ).map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(
                    if (element.tagName().equals("a")) {
                        element.absUrl("href")
                    } else {
                        element.selectFirst("a")!!.absUrl("href")
                    },
                )
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.selectFirst("div.text-sm")!!.text()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, response.request.url.pathSegments.getOrNull(1) == "romance")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/latest?serie_type=webtoon&main_genres=romance" +
            if (page > 1) {
                "&page=$page"
            } else {
                ""
            }

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("div[wire:snapshot*=App\\\\Models\\\\Serie]").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                title = element.select("div.flex a[href*=/series/]").last()!!.text()
            }
        }
        val hasNextPage = document.selectFirst("[role=navigation] button[wire:click*=nextPage]") != null

        return MangasPage(entries, hasNextPage)
    }

    private var filters: List<FilterData> = emptyList()
    private val scope = CoroutineScope(Dispatchers.IO)
    protected fun launchIO(block: () -> Unit) = scope.launch {
        try {
            block()
        } catch (_: Exception) { }
    }

    override fun getFilterList(): FilterList {
        launchIO {
            if (filters.isEmpty()) {
                val document = client.newCall(GET("$baseUrl/search", headers)).execute().asJsoup()

                val mainGenre = FilterData(
                    displayName = document.select("label[for$=main_genres]").text(),
                    options = document.select("select[wire:model.live=main_genres] option").map {
                        it.text() to it.attr("value")
                    },
                    queryParameter = "main_genres",
                )
                val typeFilter = FilterData(
                    displayName = document.select("label[for$=type]").text(),
                    options = document.select("select[wire:model.live=type] option").map {
                        it.text() to it.attr("value")
                    },
                    queryParameter = "type",
                )
                val statusFilter = FilterData(
                    displayName = document.select("label[for$=status]").text(),
                    options = document.select("select[wire:model.live=status] option").map {
                        it.text() to it.attr("value")
                    },
                    queryParameter = "status",
                )
                val genreFilter = FilterData(
                    displayName = if (lang == "ar") {
                        "التصنيفات"
                    } else {
                        "Genre"
                    },
                    options = document.select("div[x-data*=genre] > div").map {
                        it.text() to it.attr("wire:key")
                    },
                    queryParameter = "genre",
                )

                filters = listOf(mainGenre, typeFilter, statusFilter, genreFilter)
            }
        }

        val filters: List<Filter<*>> = filters.map {
            SelectFilter(
                it.displayName,
                it.options,
                it.queryParameter,
            )
        }.ifEmpty {
            listOf(
                Filter.Header("Press 'reset' to load filters"),
            )
        }

        return FilterList(filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("serie_type", "webtoon")
            addQueryParameter("title", query.trim())
            filters.filterIsInstance<SelectFilter>().forEach {
                it.addFilterParameter(this)
            }
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("#full_model h3").text()
            thumbnail_url = document.selectFirst("main img[src*=series/webtoon]")?.absUrl("src")
            status = when (document.getQueryParam("status")) {
                "ongoing", "soon" -> SManga.ONGOING
                "completed", "droped" -> SManga.COMPLETED
                "onhold" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                document.getQueryParam("type")
                    ?.capitalize()?.let(::add)
                document.select("#full_model a[href*=search?genre]")
                    .eachText().let(::addAll)
            }.joinToString()
            author = document.select("#full_model [wire:key^=a-]").eachText().joinToString()
            artist = document.select("#full_model [wire:key^=r-]").eachText().joinToString()
            description = buildString {
                append(document.select("#full_model p").text().trim())
                append("\n\nAlternative Names:\n")
                document.select("#full_model [wire:key^=n-]")
                    .joinToString("\n") { "• ${it.text().trim().removeMdEscaped()}" }
                    .let(::append)
            }.trim()
        }
    }

    private fun Document.getQueryParam(queryParam: String): String? {
        return selectFirst("#full_model a[href*=search?$queryParam]")
            ?.absUrl("href")?.toHttpUrlOrNull()?.queryParameter(queryParam)
    }

    private fun String.capitalize(): String {
        val result = StringBuilder(length)
        var capitalize = true
        for (char in this) {
            result.append(
                if (capitalize) {
                    char.uppercase()
                } else {
                    char.lowercase()
                },
            )
            capitalize = char.isWhitespace()
        }
        return result.toString()
    }

    private val mdRegex = Regex("""&amp;#(\d+);""")

    private fun String.removeMdEscaped(): String {
        val char = mdRegex.find(this)?.groupValues?.get(1)?.toIntOrNull()
            ?: return this

        return replaceFirst(mdRegex, Char(char).toString())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("a[href*=/read/]:not([type=button])").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.select("span.font-normal").text()
                date_upload = element.selectFirst("div:not(:has(> svg)) > span.text-xs")
                    ?.text().parseRelativeDate()
            }
        }
    }

    protected open fun String?.parseRelativeDate(): Long {
        this ?: return 0L

        val number = Regex("""(\d+)""").find(this)?.value?.toIntOrNull() ?: 0
        val cal = Calendar.getInstance()

        return when {
            listOf("second", "ثانية").any { contains(it, true) } -> {
                cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            }

            contains("دقيقتين", true) -> {
                cal.apply { add(Calendar.MINUTE, -2) }.timeInMillis
            }
            listOf("minute", "دقائق").any { contains(it, true) } -> {
                cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            }

            contains("ساعتان", true) -> {
                cal.apply { add(Calendar.HOUR, -2) }.timeInMillis
            }
            listOf("hour", "ساعات").any { contains(it, true) } -> {
                cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            }

            contains("يوم", true) -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
            }
            contains("يومين", true) -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -2) }.timeInMillis
            }
            listOf("day", "أيام").any { contains(it, true) } -> {
                cal.apply { add(Calendar.DAY_OF_YEAR, -number) }.timeInMillis
            }

            contains("أسبوع", true) -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -1) }.timeInMillis
            }
            contains("أسبوعين", true) -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -2) }.timeInMillis
            }
            listOf("week", "أسابيع").any { contains(it, true) } -> {
                cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
            }

            contains("شهر", true) -> {
                cal.apply { add(Calendar.MONTH, -1) }.timeInMillis
            }
            contains("شهرين", true) -> {
                cal.apply { add(Calendar.MONTH, -2) }.timeInMillis
            }
            listOf("month", "أشهر").any { contains(it, true) } -> {
                cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            }

            contains("سنة", true) -> {
                cal.apply { add(Calendar.YEAR, -1) }.timeInMillis
            }
            contains("سنتان", true) -> {
                cal.apply { add(Calendar.YEAR, -2) }.timeInMillis
            }
            listOf("year", "سنوات").any { contains(it, true) } -> {
                cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            }

            else -> 0L
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("[wire:key^=image] img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
