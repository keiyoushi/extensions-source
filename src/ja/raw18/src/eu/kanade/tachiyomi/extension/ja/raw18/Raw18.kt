package eu.kanade.tachiyomi.extension.ja.raw18

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Raw18 : WPComics("Raw18", "https://raw18.net", "ja", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.JAPANESE), null) {
    override val searchPath = "search/manga"

    override val genresUrlDelimiter = "="

    private val regex = Regex("([0-9]+)(.+)")

    override fun popularMangaSelector() = "div.items article.item"

    override fun popularMangaNextPageSelector() = "li:nth-last-child(2) a.page-link"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.selectFirst("article#item-detail").let { info ->
                author = info?.selectFirst("li.author p.col-xs-8")?.text()
                status = info?.selectFirst("li.status p.col-xs-8")?.text().toStatus()
                genre = info?.select("li.kind p.col-xs-8 a")?.joinToString { it.text() }
                description = info?.selectFirst("div.detail-content")?.text()
                thumbnail_url = imageOrNull(info?.selectFirst("div.col-image img")!!)
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/manga".toHttpUrl().newBuilder().apply {
            filters.ifEmpty { getFilterList() }.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addQueryParameter("genre", it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    else -> {}
                }
            }
            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapterDate = element.selectFirst("div.col-xs-4")!!.text()
        return SChapter.create().apply {
            element.selectFirst("a").let {
                name = it!!.text()
                setUrlWithoutDomain(it.absUrl("href"))
                date_upload = chapterDate.toDate()
            }
        }
    }

    override fun String?.toDate(): Long {
        val minuteWords = listOf("minute", "分")
        val hourWords = listOf("hour", "時間")
        val dayWords = listOf("day", "日")
        val weekWords = listOf("week", "週間")
        val monthWords = listOf("month", "月")

        try {
            val (firstMatch, secondMatch) = regex.matchEntire(this!!.substringBefore("前"))!!.destructured
            val trimmedDate = listOf(firstMatch, secondMatch)
            val calendar = Calendar.getInstance()
            when {
                monthWords.any {
                    trimmedDate[1].contains(
                        it,
                        ignoreCase = true,
                    )
                } -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                weekWords.any {
                    trimmedDate[1].contains(
                        it,
                        ignoreCase = true,
                    )
                } -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
                dayWords.any {
                    trimmedDate[1].contains(
                        it,
                        ignoreCase = true,
                    )
                } -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
                hourWords.any {
                    trimmedDate[1].contains(
                        it,
                        ignoreCase = true,
                    )
                } -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
                minuteWords.any {
                    trimmedDate[1].contains(
                        it,
                        ignoreCase = true,
                    )
                } -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            }
            return calendar.timeInMillis
        } catch (_: Exception) {
            return 0L
        }
    }

    override fun getStatusList(): List<Pair<String?, String>> {
        return listOf(
            Pair(null, "全て"),
            Pair("ongoing", "Ongoing"),
        )
    }

    override fun parseGenres(document: Document): List<Pair<String?, String>> {
        return buildList {
            add(null to "全てのジャンル")
            document.select(genresSelector).mapTo(this) { element ->
                element.attr("href")
                    .removeSuffix("/")
                    .substringAfterLast(genresUrlDelimiter) to element.text()
            }
        }
    }
}
