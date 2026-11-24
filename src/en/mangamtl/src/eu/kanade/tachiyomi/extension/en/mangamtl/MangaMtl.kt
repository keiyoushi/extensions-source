package eu.kanade.tachiyomi.extension.en.mangamtl

import eu.kanade.tachiyomi.multisrc.manhuarm.Manhuarm
import eu.kanade.tachiyomi.multisrc.manhuarm.getFilters
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.JsonElement

@Source
abstract class MangaMtl : Manhuarm() {

    override suspend fun getPopularManga(page: Int): MangasPage = archive(page, "trending")

    override suspend fun getLatestUpdates(page: Int): MangasPage = archive(page, "latest")

    // The archive keeps the stock Madara cards and sort parameter
    private suspend fun archive(page: Int, sort: String): MangasPage {
        val path = if (page == 1) "/manga/" else "/manga/page/$page/"
        val document = client.get("$baseUrl$path?m_orderby=$sort", Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("div.manga-card").map { element ->
            val link = element.selectFirst(".manga-title a")!!
            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = element.selectFirst(".manga-thumb img")?.imageUrl()
            }
        }
        return MangasPage(mangas, document.selectFirst("link[rel=next]") != null)
    }

    override fun getFilterList(data: JsonElement?) = getFilters(SORTS, GENRES, STATUSES)
}

private val SORTS = listOf(
    "Most relevant" to "relevance",
    "Recently updated" to "latest",
    "Least recently updated" to "latest_asc",
    "Trending this week" to "trending",
    "Most viewed this month" to "views_month",
    "Most viewed of all time" to "views",
    "Highest rated" to "rating",
    "Most bookmarked" to "bookmarks",
    "Newest added" to "new",
    "Oldest added" to "new_asc",
    "Release year: newest" to "year_new",
    "Release year: oldest" to "year_old",
    "Title A-Z" to "az",
    "Title Z-A" to "za",
    "Most chapters" to "chapters",
    "Fewest chapters" to "chapters_asc",
)

private val GENRES = listOf(
    "Action" to "action",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Comedy" to "comedy",
    "Doujinshi" to "doujinshi",
    "Drama" to "drama",
    "Ecchi" to "ecchi",
    "Fantasy" to "fantasy",
    "Gender Bender" to "gender-bender",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Historical" to "historical",
    "Horror" to "horror",
    "Josei" to "josei",
    "Lolicon" to "lolicon",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mecha" to "mecha",
    "Mystery" to "mystery",
    "Psychological" to "psychological",
    "Romance" to "romance",
    "School Life" to "school-life",
    "Sci-Fi" to "sci-fi",
    "Seinen" to "seinen",
    "Shotacon" to "shotacon",
    "Shoujo" to "shoujo",
    "Shoujo Ai" to "shoujo-ai",
    "Shounen" to "shounen",
    "Shounen Ai" to "shounen-ai",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Supernatural" to "supernatural",
    "Tragedy" to "tragedy",
    "Yaoi" to "yaoi",
    "Yuri" to "yuri",
)

private val STATUSES = listOf(
    "OnGoing" to "on-going",
    "Completed" to "end",
    "Canceled" to "canceled",
    "On Hold" to "on-hold",
    "Upcoming" to "upcoming",
)
