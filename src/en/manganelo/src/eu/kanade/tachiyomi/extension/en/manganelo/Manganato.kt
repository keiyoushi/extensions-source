package eu.kanade.tachiyomi.extension.en.manganelo

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class Manganato : MangaBox("Manganato", "https://www.natomanga.com", "en") {
    override val id: Long = 1024627298672457456

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM-dd-yyyy HH:mm", Locale.ENGLISH)

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("Referer", "$baseUrl/") // for covers
    override val popularUrlPath = "manga-list/hot-manga?page="
    override val latestUrlPath = "manga-list/latest-manga?page="
    override val simpleQueryPath = "search/story/"
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank() && getAdvancedGenreFilters().isEmpty()) {
            val url = "$baseUrl/$simpleQueryPath".toHttpUrl().newBuilder()
                .addPathSegment(normalizeSearchQuery(query))
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        } else {
            val url = "$baseUrl/genre".toHttpUrl().newBuilder()
            url.addQueryParameter("page", page.toString())
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> url.addQueryParameter("type", filter.toUriPart())
                    is StatusFilter -> url.addQueryParameter("state", filter.toUriPart())
                    is GenreFilter -> url.addPathSegment(filter.toUriPart()!!)
                    else -> {}
                }
            }

            GET(url.build(), headers)
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        // parse on title attribute rather than the value
        val dateUploadAttr: Long? = try {
            dateFormat.parse(element.selectDateFromElement().attr("title"))?.time
        } catch (e: ParseException) {
            null
        }

        return super.chapterFromElement(element).apply {
            date_upload = dateUploadAttr ?: date_upload
        }
    }

    override val descriptionSelector = "div#contentBox"

    override fun imageRequest(page: Page): Request {
        return if (page.url.contains(baseUrl)) {
            GET(page.imageUrl!!, headersBuilder().build())
        } else { // Avoid 403 errors on non-migrated mangas
            super.imageRequest(page)
        }
    }

    override fun getGenreFilters(): Array<Pair<String?, String>> = arrayOf(
        Pair("all", "ALL"),
        Pair("action", "Action"),
        Pair("adult", "Adult"),
        Pair("adventure", "Adventure"),
        Pair("comedy", "Comedy"),
        Pair("cooking", "Cooking"),
        Pair("doujinshi", "Doujinshi"),
        Pair("drama", "Drama"),
        Pair("ecchi", "Ecchi"),
        Pair("fantasy", "Fantasy"),
        Pair("gender-bender", "Gender bender"),
        Pair("harem", "Harem"),
        Pair("historical", "Historical"),
        Pair("horror", "Horror"),
        Pair("isekai", "Isekai"),
        Pair("josei", "Josei"),
        Pair("manhua", "Manhua"),
        Pair("manhwa", "Manhwa"),
        Pair("martial-arts", "Martial arts"),
        Pair("mature", "Mature"),
        Pair("mecha", "Mecha"),
        Pair("medical", "Medical"),
        Pair("mystery", "Mystery"),
        Pair("one-shot", "One shot"),
        Pair("psychological", "Psychological"),
        Pair("romance", "Romance"),
        Pair("school-life", "School life"),
        Pair("sci-fi", "Sci fi"),
        Pair("seinen", "Seinen"),
        Pair("shoujo", "Shoujo"),
        Pair("shoujo-ai", "Shoujo ai"),
        Pair("shounen", "Shounen"),
        Pair("shounen-ai", "Shounen ai"),
        Pair("slice-of-life", "Slice of life"),
        Pair("smut", "Smut"),
        Pair("sports", "Sports"),
        Pair("supernatural", "Supernatural"),
        Pair("tragedy", "Tragedy"),
        Pair("webtoons", "Webtoons"),
        Pair("yaoi", "Yaoi"),
        Pair("yuri", "Yuri"),
    )
}
