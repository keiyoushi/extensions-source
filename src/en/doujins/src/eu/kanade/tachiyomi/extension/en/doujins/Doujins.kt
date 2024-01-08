package eu.kanade.tachiyomi.extension.en.doujins

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class Doujins : HttpSource() {

    override val baseUrl: String = "https://doujins.com"

    override val lang: String = "en"

    override val name: String = "Doujins"

    override val supportsLatest: Boolean = true

    private val json: Json by injectLazy()

    override fun chapterListParse(response: Response): List<SChapter> {
        return listOf(
            SChapter.create().apply {
                val element = response.asJsoup()
                name = "Chapter"
                scanlator = element.select("div.folder-message:contains(Translated)").text().substringAfter("by:").trim()
                setUrlWithoutDomain(response.request.url.toString())

                val dateAndPageCountString = element.select(".text-md-right.text-sm-left > .folder-message").text()

                val date = dateAndPageCountString.substringBefore(" • ")
                for (dateFormat in MANGA_DETAILS_DATE_FORMAT) {
                    if (date_upload == 0L) {
                        date_upload = dateFormat.parseOrNull(date)?.time ?: 0L
                    } else {
                        break
                    }
                }
            },
        )
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage {
        return MangasPage(
            json.decodeFromString<JsonObject>(response.body.string())["folders"]!!.jsonArray.map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.jsonObject["link"]!!.jsonPrimitive.content)
                    title = it.jsonObject["name"]!!.jsonPrimitive.content
                    artist = it.jsonObject["artistList"]!!.jsonPrimitive.content
                    author = artist
                    genre = it.jsonObject["tags"]!!.jsonArray.joinToString(", ") { it.jsonObject["tag"]!!.jsonPrimitive.content }
                    thumbnail_url = it.jsonObject["thumbnail2"]!!.jsonPrimitive.content
                }
            },
            true,
        )
    }

    private fun getLatestPageUrl(page: Int): String {
        val endDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DATE, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DATE, -1 * PAGE_DAYS * (page - 1))
        }

        val endDateSec = endDate.timeInMillis / 1000
        val startDateSec = endDate.apply {
            add(Calendar.DATE, -1 * PAGE_DAYS)
        }.timeInMillis / 1000

        return "$baseUrl/folders?start=$startDateSec&end=$endDateSec"
    }

    override fun latestUpdatesRequest(page: Int) = GET(getLatestPageUrl(page))

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.select(".folder-title a").last()!!.text()
            artist = document.select(".gallery-artist a").joinToString { it.text() }
            author = artist
            genre = document.select(".tag-area").first()!!.select("a").joinToString { it.text() }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pageUrl = response.request.url.toString()
        return document.select(".doujin").mapIndexed { i, page ->
            Page(i, "$pageUrl${page.attr("data-link")}", page.attr("data-file").replace("amp;", ""))
        }
    }

    override fun popularMangaParse(response: Response) = parseGalleryPage(response.asJsoup())

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top/month")

    override fun searchMangaParse(response: Response) = parseGalleryPage(response.asJsoup())

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val seriesFilter = filterList.findInstance<SeriesFilter>()!!
        val sortFilter = filterList.findInstance<SortFilter>()!!
        val popularityPeriodFilter = filterList.findInstance<PopularityPeriodFilter>()!!

        return when {
            query != "" -> {
                GET("$baseUrl/searches?words=$query&page=$page&sort=${sortFilter.toUriPart()}")
            }
            seriesFilter.toUriPart() != "" -> {
                GET("$baseUrl${seriesFilter.toUriPart()}?sort=${sortFilter.toUriPart()}")
            }
            else -> {
                GET("$baseUrl${popularityPeriodFilter.toUriPart()}")
            }
        }
    }

    private fun parseGalleryPage(document: Document): MangasPage {
        val pagination = document.select(".pagination").first()
        return MangasPage(
            document.select("div:not(.premium-folder) > .thumbnail-doujin a.gallery-visited-from-favorites").map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.select("div.title .text").text()
                    artist = it.parent()!!.nextElementSibling()!!.select(".single-line strong").last()
                        ?.text()?.substringAfter("Artist: ")
                    author = artist
                    thumbnail_url = it.select("img").attr("srcset")
                }
            },
            if (pagination != null) {
                !pagination.select("li.page-item:last-child").hasClass("disabled")
            } else {
                false
            },
        )
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Text search ignores series and period filters"),
        Filter.Separator(),

        Filter.Header("Series filter overrides period filter"),
        SeriesFilter(),
        Filter.Separator(),

        Filter.Header("Period filter only applies at initial page"),
        PopularityPeriodFilter(),
        Filter.Separator(),

        Filter.Header("Sort only works with text search and series filter"),
        SortFilter(),
    )

    private class SeriesFilter : UriPartFilter(
        "Series",
        arrayOf(
            Pair("None", ""),
            Pair("Doujins - Original Series", "/doujins-original-series-19934"),
            Pair("Hentai Magazine Chapters", "/hentai-magazine-chapters-2766"),
            Pair("Hentai Manga", "/hentai-manga-19"),
            Pair("Fate Grand Order", "/fate-grand-order-doujins-28615"),
            Pair("CG Sets - Original Series", "/cg-sets-original-series-14865"),
            Pair("Touhou", "/touhou-doujins-7748"),
            Pair("Naruto", "/naruto-doujins-5761"),
            Pair("Kantai Collection", "/kantai-collection-doujins-22720"),
            Pair("Hentai Game CG-Sets", "/hentai-game-cg-sets-2422"),
            Pair("One Piece", "/one-piece-doujins-6080"),
            Pair("Granblue Fantasy", "/granblue-fantasy-doujins-28177"),
            Pair("Azur Lane", "/azur-lane-doujins-34298"),
            Pair("Sword Art Online", "/sword-art-online-doujins-7246"),
            Pair("Idolmaster", "/idolmaster-4281"),
            Pair("My Hero Academia", "/my-hero-academia-doujins-28744"),
            Pair("Love Live", "/love-live-doujins-21865"),
            Pair("Pokemon", "/pokemon-doujins-6393"),
            Pair("Dragon Ball", "/dragon-ball-doujins-1238"),
            Pair("CGs - Mixed Series", "/cgs-mixed-series-35311"),
            Pair("Doujins - Mixed Series", "/doujins-mixed-series-20091"),
            Pair("Hentai Magazine Chapters", "/hentai-magazine-chapters-2766"),
            Pair("Hentai Magazine Chapters - Super-Shorts", "/hentai-magazine-chapters-super-shorts-19933"),
            Pair("Hentai Manga", "/hentai-manga-19"),
        ),
    )

    private class SortFilter : UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Newest First", ""),
            Pair("Oldest First", "created_at"),
            Pair("Alphabetical", "name"),
            Pair("Rating", "-cached_score"),
            Pair("Popularity", "-cached_views"),
        ),
    )

    private class PopularityPeriodFilter : UriPartFilter(
        "Period",
        arrayOf(
            Pair("This Month", "/top"),
            Pair("This Year", "/top/year"),
            Pair("All Time", "/top/all"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun SimpleDateFormat.parseOrNull(string: String): Date? {
        return try {
            parse(string)
        } catch (e: ParseException) {
            null
        }
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    companion object {
        private const val PAGE_DAYS = 3
        private val ORDINAL_SUFFIXES = listOf("th", "st", "nd", "rd")
        private val MANGA_DETAILS_DATE_FORMAT = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("MMMM dd'$it', yyyy", Locale.US)
        }
    }
}
