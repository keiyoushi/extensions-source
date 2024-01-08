package eu.kanade.tachiyomi.extension.ja.rawdevart

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Rawdevart : ParsedHttpSource() {

    override val name = "Rawdevart"

    override val baseUrl = "https://rawdevart.com"

    override val lang = "ja"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/comic/?page=$page&lister=0")

    override fun latestUpdatesSelector() = "div.row div.hovereffect"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("a.head")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = item.text()
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun latestUpdatesNextPageSelector() = "li.page-item:not(.disabled) a[aria-label=next]"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/comic/?page=$page&lister=5")

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("page", page.toString())
        url.addQueryParameter("title", query)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is ArtistFilter -> {
                    url.addQueryParameter("artist", filter.state)
                }
                is SortFilter -> {
                    url.addQueryParameter("lister", filter.toUriPart())
                }
                is TypeFilter -> {
                    val typeToExclude = mutableListOf<String>()
                    val typeToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isExcluded()) {
                            typeToExclude.add(content.id)
                        } else if (content.isIncluded()) {
                            typeToInclude.add(content.id)
                        }
                    }
                    if (typeToExclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "ctype_exc",
                            typeToExclude
                                .joinToString(","),
                        )
                    }
                    if (typeToInclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "ctype_inc",
                            typeToInclude
                                .joinToString(","),
                        )
                    }
                }
                is StatusFilter -> {
                    val statusToExclude = mutableListOf<String>()
                    val statusToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isExcluded()) {
                            statusToExclude.add(content.id)
                        } else if (content.isIncluded()) {
                            statusToInclude.add(content.id)
                        }
                    }
                    if (statusToExclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "status_exc",
                            statusToExclude
                                .joinToString(","),
                        )
                    }
                    if (statusToInclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "status_inc",
                            statusToInclude
                                .joinToString(","),
                        )
                    }
                }
                is GenreFilter -> {
                    val genreToExclude = mutableListOf<String>()
                    val genreToInclude = mutableListOf<String>()
                    filter.state.forEach { content ->
                        if (content.isExcluded()) {
                            genreToExclude.add(content.id)
                        } else if (content.isIncluded()) {
                            genreToInclude.add(content.id)
                        }
                    }
                    if (genreToExclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "genre_exc",
                            genreToExclude
                                .joinToString(","),
                        )
                    }
                    if (genreToInclude.isNotEmpty()) {
                        url.addQueryParameter(
                            "genre_inc",
                            genreToInclude
                                .joinToString(","),
                        )
                    }
                }
                else -> {}
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.manga-main")
        val manga = SManga.create()
        val status = infoElement.select("th:contains(Status) + td").text()
        val genres = mutableListOf<String>()
        infoElement.select("div.genres a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.title = infoElement.select("h1").text()
        manga.author = infoElement.select("th:contains(Author) + td").text()
        manga.artist = infoElement.select("th:contains(Artist) + td").text()
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = infoElement.select("div.description").text()
            .substringAfter("Description ")
        manga.thumbnail_url = infoElement.select("img.img-fluid.not-lazy").attr("abs:src")

        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Finished") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga) = chapterListRequest(manga.url, 1)

    private fun chapterListRequest(mangaUrl: String, page: Int): Request {
        return GET("$baseUrl$mangaUrl?page=$page", headers)
    }

    override fun chapterListSelector() = "div.list-group-item"

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        var nextPage = 2
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        while (document.select(paginationNextPageSelector).isNotEmpty()) {
            val currentPage = document.select("form#filter_form").attr("action")
            document = client.newCall(chapterListRequest(currentPage, nextPage)).execute().asJsoup()
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            nextPage++
        }

        return chapters
    }

    private val paginationNextPageSelector = latestUpdatesNextPageSelector()

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.name = element.select("div.rounded-0 span.text-truncate").text()
        chapter.date_upload = element.select("span.mr-2").text().let {
            try {
                when {
                    it.contains("ago") -> Date(System.currentTimeMillis() - it.split("\\s".toRegex())[0].toLong() * 60 * 60 * 1000).time
                    it.contains("Yesterday") -> Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000).time
                    it.contains(".") -> SimpleDateFormat("MMM. dd, yyyy", Locale.US).parse(it)?.time ?: 0L
                    else -> SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time ?: 0L
                }
            } catch (e: Exception) {
                Date(System.currentTimeMillis()).time
            }
        }

        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.not-lazy[data-src]").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("data-src")))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    private class AuthorFilter : Filter.Text("Author")

    private class ArtistFilter : Filter.Text("Artist")

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("<select>", ""),
            Pair("Latest", "0"),
            Pair("A-Z", "1"),
            Pair("Z-A", "2"),
            Pair("Star", "3"),
            Pair("Bookmark", "4"),
            Pair("View", "5"),
        ),
    )

    private class TypeFilter(type: List<Tag>) : Filter.Group<Tag>("Types", type)

    private class StatusFilter(status: List<Tag>) : Filter.Group<Tag>("Status", status)

    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("Combine Sort filter with other filters."),
        Filter.Separator(),
        AuthorFilter(),
        ArtistFilter(),
        SortFilter(),
        TypeFilter(getTypeList()),
        StatusFilter(getStatusList()),
        GenreFilter(getGenreList()),
    )

    private fun getTypeList() = listOf(
        Tag("0", "Manga"),
        Tag("1", "Webtoon"),
        Tag("2", "Manhwa - Korean"),
        Tag("3", "Manhua - Chinese"),
        Tag("4", "Comic"),
        Tag("5", "Doujinshi"),
    )

    private fun getStatusList() = listOf(
        Tag("0", "Ongoing"),
        Tag("1", "Haitus"),
        Tag("2", "Axed"),
        Tag("3", "Unknown"),
        Tag("4", "Finished"),
    )

    private fun getGenreList() = listOf(
        Tag("29", "4-koma"),
        Tag("1", "Action"),
        Tag("37", "Adult"),
        Tag("2", "Adventure"),
        Tag("3", "Comedy"),
        Tag("33", "Cooking"),
        Tag("4", "Crime"),
        Tag("5", "Drama"),
        Tag("30", "Ecchi"),
        Tag("6", "Fantasy"),
        Tag("34", "Gender Bender"),
        Tag("31", "Gore"),
        Tag("39", "Harem"),
        Tag("7", "Historical"),
        Tag("8", "Horror"),
        Tag("9", "Isekai"),
        Tag("42", "Josei"),
        Tag("35", "Martial Arts"),
        Tag("36", "Mature"),
        Tag("10", "Mecha"),
        Tag("11", "Medical"),
        Tag("38", "Music"),
        Tag("12", "Mystery"),
        Tag("13", "Philosophical"),
        Tag("14", "Psychological"),
        Tag("15", "Romance"),
        Tag("40", "School Life"),
        Tag("16", "Sci-Fi"),
        Tag("41", "Seinen"),
        Tag("28", "Shoujo"),
        Tag("17", "Shoujo Ai"),
        Tag("27", "Shounen"),
        Tag("18", "Shounen Ai"),
        Tag("19", "Slice of Life"),
        Tag("32", "Smut"),
        Tag("20", "Sports"),
        Tag("21", "Super Powers"),
        Tag("43", "Supernatural"),
        Tag("22", "Thriller"),
        Tag("23", "Tragedy"),
        Tag("24", "Wuxia"),
        Tag("25", "Yaoi"),
        Tag("26", "Yuri"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.TriState(name)
}
