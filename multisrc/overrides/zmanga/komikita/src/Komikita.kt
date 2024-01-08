package eu.kanade.tachiyomi.extension.id.komikita

import eu.kanade.tachiyomi.multisrc.zmanga.ZManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Komikita : ZManga(
    "Komikita",
    "https://komikita.org",
    "id",
    SimpleDateFormat("MMM d, yyyy", Locale("id")),
) {
    override val hasProjectPage = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/${pagePathSegment(page)}/?s")
    }

    override fun latestUpdatesSelector() = "h2:contains(Latest) + .flexbox3 .flexbox3-item"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/${pagePathSegment(page)}")
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("div.flexbox3-content a").attr("href"))
            title = element.select("div.flexbox3-content a").attr("title")
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = "$baseUrl/${pagePathSegment(page)}".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                // if site has project page, default value "hasProjectPage" = false
                is ProjectFilter -> {
                    if (filter.toUriPart() == "project-filter-on") {
                        url =
                            "$baseUrl$projectPageString/page/$page".toHttpUrlOrNull()!!.newBuilder()
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: cant be used with multiple genre!"),
            GenreList(getGenreList()),
        )
        if (hasProjectPage) {
            filters.addAll(
                mutableListOf<Filter<*>>(
                    Filter.Separator(),
                    Filter.Header("NOTE: cant be used with other filter!"),
                    Filter.Header("$name Project List page"),
                    ProjectFilter(),
                ),
            )
        }
        return FilterList(filters)
    }

    private fun getGenreList() = listOf(
        Tag("4-koma", "4-Koma"),
        Tag("4-koma-comedy", "4-Koma Comedy"),
        Tag("action", "Action"),
        Tag("adult", "Adult"),
        Tag("adventure", "Adventure"),
        Tag("another-chance", "Another chance"),
        Tag("city", "City"),
        Tag("comedy", "Comedy"),
        Tag("completed", "Completed"),
        Tag("cooking", "Cooking"),
        Tag("demons", "Demons"),
        Tag("drama", "Drama"),
        Tag("ecchi", "Ecchi"),
        Tag("fantasy", "Fantasy"),
        Tag("full-color", "Full Color"),
        Tag("game", "Game"),
        Tag("gender-bender", "Gender bender"),
        Tag("gore", "Gore"),
        Tag("harem", "Harem"),
        Tag("historical", "Historical"),
        Tag("horror", "Horror"),
        Tag("isekai", "Isekai"),
        Tag("josei", "Josei"),
        Tag("kingdom", "Kingdom"),
        Tag("leveling", "Leveling"),
        Tag("loli", "Loli"),
        Tag("magic", "Magic"),
        Tag("manga", "Manga"),
        Tag("manhua", "Manhua"),
        Tag("manhwa", "Manhwa"),
        Tag("martial-arts", "Martial Arts"),
        Tag("mature", "Mature"),
        Tag("mecha", "Mecha"),
        Tag("medical", "Medical"),
        Tag("military", "Military"),
        Tag("monster", "Monster"),
        Tag("monsters", "Monsters"),
        Tag("monster-girls", "Monster Girls"),
        Tag("music", "Music"),
        Tag("mystery", "Mystery"),
        Tag("n-a", "N/A"),
        Tag("one-shot", "One Shot"),
        Tag("overpowered", "Overpowered"),
        Tag("parody", "Parody"),
        Tag("police", "Police"),
        Tag("post-apocalyptic", "Post Apocalyptic"),
        Tag("psychological", "Psychological"),
        Tag("reincarnation", "Reincarnation"),
        Tag("returned", "Returned"),
        Tag("returner", "Returner"),
        Tag("romance", "Romance"),
        Tag("school", "School"),
        Tag("school-life", "School Life"),
        Tag("sci-fi", "Sci-Fi"),
        Tag("socks", "Socks"),
        Tag("seinen", "Seinen"),
        Tag("shoujo", "Shoujo"),
        Tag("shoujo-ai", "Shoujo Ai"),
        Tag("shounen", "Shounen"),
        Tag("shounen-ai", "Shounen Ai"),
        Tag("slice-of-life", "Slice of Life"),
        Tag("smut", "Smut"),
        Tag("sports", "Sports"),
        Tag("super-power", "Super Power"),
        Tag("supernatural", "Supernatural"),
        Tag("survival", "Survival"),
        Tag("terror", "Terror"),
        Tag("thriller", "Thriller"),
        Tag("tragedy", "Tragedy"),
        Tag("vampire", "Vampire"),
        Tag("webtoons", "Webtoons"),
        Tag("yuri", "Yuri"),
        Tag("zombies", "Zombies"),
    )

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)

    private class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
}
