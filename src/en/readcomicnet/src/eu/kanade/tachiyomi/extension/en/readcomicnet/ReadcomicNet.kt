package eu.kanade.tachiyomi.extension.en.readcomicnet

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ReadcomicNet : ParsedHttpSource() {

    override val name = "ReadComicNet"

    override val baseUrl = "https://readcomic.net"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaSelector() = ".manga-box"

    override fun latestUpdatesSelector() = ".home-list .hl-box .hlb-name"

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaNextPageSelector() = ".general-nav a:last-child"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular-comic/$page", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comic-updates/$page", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            "$baseUrl/advanced-search".toHttpUrl().newBuilder().apply {
                addQueryParameter("key", query)
                addQueryParameter("page", page.toString())
                if (!filters.isEmpty()) {
                    for (filter in filters) {
                        when (filter) {
                            is StatusFilter -> {
                                addQueryParameter("status", filter.stateAsQueryString())
                            }

                            is GenreList -> {
                                addQueryParameter("wg", filter.included.joinToString(","))
                                addQueryParameter("wog", filter.excluded.joinToString(","))
                            }

                            else -> {}
                        }
                    }
                }
            }.build(),
            headers,
        )

    override fun popularMangaFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
            title = element.selectFirst("h3")!!.text()
            thumbnail_url = element.selectFirst(".image")?.selectFirst("img")?.attr("abs:src")
        }

    override fun latestUpdatesFromElement(element: Element): SManga =
        SManga.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            title = element.text()
        }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.selectFirst(".manga-details tbody")!!

        author = infoElement.selectFirst("td:contains(Author:)+ td")?.text()
        genre = infoElement.selectFirst("td:contains(Genre:)+ td")?.text()
        status = infoElement.selectFirst("td:contains(Status:)+ td")?.text()
            .orEmpty().let { parseStatus(it) }
        description = document.select(".pdesc").first()?.text()
        thumbnail_url = document.select(".manga-image img").first()?.attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = ".ch-name"

    private val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href") + "/full")
        name = element.text()
        try {
            date_upload = element.nextElementSibling()!!.text().let {
                dateFormat.parse(it)?.time ?: 0L
            }
        } catch (exception: Exception) {
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".chapter_img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.attr("abs:data-original"))
        }
    }

    private class StatusFilter :
        Filter.Select<String>("Status", arrayOf("", "Complete", "On Going"), 0) {
        val stateArray = arrayOf("", "CMP", "ONG")
        fun stateAsQueryString(): String {
            return stateArray[this.state]
        }
    }

    private class Genre(name: String, val gid: String) : Filter.TriState(name)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.gid }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.gid }
    }

    override fun getFilterList() = FilterList(
        StatusFilter(),
        GenreList(getGenreList()),
    )

    override fun imageUrlParse(document: Document): String = ""

    // [...document.querySelectorAll(".search-checks li")].map((el) => `Genre("${el.innerText}", "${el.innerText.replaceAll(" ","+")}")`).join(',\n')
    // on https://readcomic.net/advanced-search
    private fun getGenreList() = listOf(
        Genre("Marvel", "Marvel"),
        Genre("DC Comics", "DC+Comics"),
        Genre("Action", "Action"),
        Genre("Adventure", "Adventure"),
        Genre("Anthology", "Anthology"),
        Genre("Anthropomorphic", "Anthropomorphic"),
        Genre("Biography", "Biography"),
        Genre("Children", "Children"),
        Genre("Comedy", "Comedy"),
        Genre("Crime", "Crime"),
        Genre("Cyborgs", "Cyborgs"),
        Genre("Dark Horse", "Dark+Horse"),
        Genre("Demons", "Demons"),
        Genre("Drama", "Drama"),
        Genre("Fantasy", "Fantasy"),
        Genre("Family", "Family"),
        Genre("Fighting", "Fighting"),
        Genre("Gore", "Gore"),
        Genre("Graphic Novels", "Graphic+Novels"),
        Genre("Historical", "Historical"),
        Genre("Horror", "Horror"),
        Genre("Leading Ladies", "Leading+Ladies"),
        Genre("Literature", "Literature"),
        Genre("Magic", "Magic"),
        Genre("Manga", "Manga"),
        Genre("Martial Arts", "Martial+Arts"),
        Genre("Mature", "Mature"),
        Genre("Mecha", "Mecha"),
        Genre("Military", "Military"),
        Genre("Movie Cinematic Link", "Movie+Cinematic+Link"),
        Genre("Mystery", "Mystery"),
        Genre("Mythology", "Mythology"),
        Genre("Psychological", "Psychological"),
        Genre("Personal", "Personal"),
        Genre("Political", "Political"),
        Genre("Post-Apocalyptic", "Post-Apocalyptic"),
        Genre("Pulp", "Pulp"),
        Genre("Robots", "Robots"),
        Genre("Romance", "Romance"),
        Genre("Sci-Fi", "Sci-Fi"),
        Genre("Slice of Life", "Slice+of+Life"),
        Genre("Science Fiction", "Science+Fiction"),
        Genre("Sports", "Sports"),
        Genre("Spy", "Spy"),
        Genre("Superhero", "Superhero"),
        Genre("Supernatural", "Supernatural"),
        Genre("Suspense", "Suspense"),
        Genre("Thriller", "Thriller"),
        Genre("Tragedy", "Tragedy"),
        Genre("Vampires", "Vampires"),
        Genre("Vertigo", "Vertigo"),
        Genre("Video Games", "Video+Games"),
        Genre("War", "War"),
        Genre("Western", "Western"),
        Genre("Zombies", "Zombies"),
    )
}
