package eu.kanade.tachiyomi.extension.en.timelesstoons

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class TimelessToons :
    Keyoapp(
        "TimelessToons",
        "https://timelesstoons.org",
        "en",
    ) {

    override fun popularMangaSelector() = "div:has(> h2:contains(Trending)) + div .group"

    override fun latestUpdatesSelector() = "div.grid > div.group.latest-poster"

    override val genreSelector = "div.grid:has(>h1) a[href*=\"genre=\"]"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        document.getImageUrl("div[style*=photoURL]")?.let {
            manga.thumbnail_url = it
        }

        manga.genre = manga.genre
            ?.split(", ")
            ?.mapNotNull { it.trim(',', ' ').takeIf(String::isNotBlank) }
            ?.joinToString(", ")

        return manga
    }

    override fun fetchGenres() {}

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("")
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("genre", it.id)
                        }
                    }
                    is TypeFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("type", it.id)
                        }
                    }
                    is StatusFilter -> {
                        filter.state.filter { it.state }.forEach {
                            addQueryParameter("status", it.id)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val url = response.request.url

        val query = url.queryParameter("q")?.takeIf { it.isNotBlank() }
        val genres = url.queryParameterValues("genre").filterNotNull().takeIf { it.isNotEmpty() }
        val types = url.queryParameterValues("type").filterNotNull().takeIf { it.isNotEmpty() }
        val statuses = url.queryParameterValues("status").filterNotNull().takeIf { it.isNotEmpty() }

        val mangaList = document.select(searchMangaSelector())
            .filter { entry ->
                val titleMatch = query == null ||
                    entry.attr("title").contains(query, ignoreCase = true)

                val genreMatch = genres == null || run {
                    val tagsAttr = entry.attr("tags").replace("___", "'")
                    val entryGenres = runCatching {
                        tagsAttr.parseAs<List<String>>()
                    }.getOrDefault(emptyList())
                    genres.all { genre -> entryGenres.any { it.equals(genre, ignoreCase = true) } }
                }

                val typeMatch = types == null ||
                    types.any { it.equals(entry.attr("data-type"), ignoreCase = true) }

                val statusMatch = statuses == null ||
                    statuses.any { it.equals(entry.attr("data-status"), ignoreCase = true) }

                titleMatch && genreMatch && typeMatch && statusMatch
            }
            .map(::searchMangaFromElement)

        return MangasPage(mangaList, false)
    }

    override fun getFilterList() = FilterList(
        GenreList("Genres", getGenreList()),
        TypeFilter(getTypeList()),
        StatusFilter(getStatusList()),
    )

    class Type(name: String, val id: String) : Filter.CheckBox(name)
    class TypeFilter(types: List<Type>) : Filter.Group<Type>("Type", types)

    class Status(name: String, val id: String) : Filter.CheckBox(name)
    class StatusFilter(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    private fun getTypeList() = listOf(
        Type("Manhwa", "manhwa"),
        Type("Manhua", "manhua"),
        Type("Manga", "manga"),
        Type("Mangatoon", "mangatoon"),
        Type("Comic", "comic"),
    )

    private fun getStatusList() = listOf(
        Status("Ongoing", "ongoing"),
        Status("Completed", "completed"),
        Status("Dropped", "dropped"),
        Status("Hiatus", "hiatus"),
    )

    private fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Alter ego", "alter ego"),
        Genre("Animal", "animal"),
        Genre("Another world", "another world"),
        Genre("Assassin", "assassin"),
        Genre("Childcare", "childcare"),
        Genre("Clumsy fl", "clumsy fl"),
        Genre("Comedy", "comedy"),
        Genre("Cultivation", "cultivation"),
        Genre("Demon", "demon"),
        Genre("Doctor", "doctor"),
        Genre("Drama", "drama"),
        Genre("Dungeon", "dungeon"),
        Genre("Ecchi", "ecchi"),
        Genre("Family", "family"),
        Genre("Fantasy", "fantasy"),
        Genre("Female lead", "female lead"),
        Genre("Football", "football"),
        Genre("Game world", "game world"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Hot ml", "hot ml"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial arts", "martial arts"),
        Genre("Medicine", "medicine"),
        Genre("Monsters", "monsters"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Office", "office"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Psychology", "psychology"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Revenge", "revenge"),
        Genre("Rofan", "rofan"),
        Genre("Romance", "romance"),
        Genre("School life", "school life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Serial killer", "serial killer"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of life", "slice of life"),
        Genre("Slow life", "slow life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super power", "super power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Suspense", "suspense"),
        Genre("System", "system"),
        Genre("Taboo", "taboo"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Transmigation", "transmigation"),
        Genre("Villainess", "villainess"),
        Genre("Wuxia", "wuxia"),
        Genre("Young adult", "young adult"),
        Genre("Youth", "youth"),
    )
}
