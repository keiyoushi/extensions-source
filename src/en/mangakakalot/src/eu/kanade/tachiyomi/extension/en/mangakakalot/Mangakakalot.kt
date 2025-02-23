package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class Mangakakalot : MangaBox("Mangakakalot", "https://www.mangakakalot.gg", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("Referer", "https://www.mangakakalot.gg/") // for covers
    override val popularUrlPath = "manga-list/hot-manga?page="
    override val latestUrlPath = "manga-list/latest-manga?page="
    override val simpleQueryPath = "search/story/"
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank() && getAdvancedGenreFilters().isEmpty()) {
            GET("$baseUrl/$simpleQueryPath${normalizeSearchQuery(query)}?page=$page", headers)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
            url.addPathSegment("genre")
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

    override val descriptionSelector = "div#contentBox"

    override fun imageRequest(page: Page): Request {
        return if (page.url.contains(baseUrl)) {
            GET(page.imageUrl!!, headersBuilder().build())
        } else { //Avoid 403 errors on non-migrated mangas
            GET(page.imageUrl!!, headersBuilder().set("Referer", baseUrl).build())
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
