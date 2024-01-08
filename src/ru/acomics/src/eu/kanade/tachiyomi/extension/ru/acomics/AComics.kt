package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

class AComics : ParsedHttpSource() {

    override val name = "AComics"

    override val baseUrl = "https://acomics.ru"

    override val lang = "ru"

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()
        cookies["ageRestrict"] = "17"
        buildCookies(cookies)
    }

    private fun buildCookies(cookies: Map<String, String>) =
        cookies.entries.joinToString(separator = "; ", postfix = ";") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

    override val client = network.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .addHeader("Cookie", cookiesHeader)
                .build()

            chain.proceed(newReq)
        }.build()

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/comics?categories=&ratings[]=1&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5ratings[]=6&&type=0&updatable=0&subscribe=0&issue_count=2&sort=subscr_count&skip=${10 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/comics?categories=&ratings[]=1&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5ratings[]=6&&type=0&updatable=0&subscribe=0&issue_count=2&sort=last_update&skip=${10 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url: String = if (query.isNotEmpty()) {
            "$baseUrl/search?keyword=$query"
        } else {
            val categories = mutableListOf<Int>()
            var status = "0"
            val rating = mutableListOf<Int>()
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach {
                            if (it.state) {
                                categories.add(it.id)
                            }
                        }
                    }
                    is Status -> {
                        if (filter.state == 1) {
                            status = "no"
                        }
                        if (filter.state == 2) {
                            status = "yes"
                        }
                    }
                    is RatingList -> {
                        filter.state.forEach {
                            if (it.state) {
                                rating.add(it.id)
                            }
                        }
                    }
                    else -> {}
                }
            }
            "$baseUrl/comics?categories=${categories.joinToString(",")}&${rating.joinToString { "ratings[]=$it" }}&type=0&updatable=$status&subscribe=0&issue_count=2&sort=subscr_count&skip=${10 * (page - 1)}"
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "table.list-loadable > tbody > tr"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = baseUrl + element.select("a > img").first()!!.attr("src")
        element.select("div.title > a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href") + "/about")
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "span.button:not(:has(a)) + span.button > a"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".about-summary").first()!!
        val manga = SManga.create()
        manga.author = infoElement.select(".about-summary > p:contains(Автор)").text().split(":")[1]
        manga.genre = infoElement.select("a.button").joinToString { it.text() }
        manga.description = infoElement.ownText()
        return manga
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val res = mutableListOf<SChapter>()
        val count = response.asJsoup()
            .select(".about-summary > p:contains(Количество выпусков:)")
            .text()
            .split("Количество выпусков: ")[1].toInt()

        for (index in count downTo 1) {
            val chapter = SChapter.create()
            chapter.chapter_number = index.toFloat()
            chapter.name = index.toString()
            val url = response.request.url.toString().split("/about")[0].split(baseUrl)[1]
            chapter.url = "$url/$index"
            res.add(chapter)
        }
        return res
    }

    override fun chapterListSelector(): Nothing = throw Exception("Not used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    override fun pageListParse(document: Document): List<Page> {
        val imageElement = document.select("img#mainImage").first()!!
        return listOf(Page(0, imageUrl = baseUrl + imageElement.attr("src")))
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Категории", genres)
    private class Genre(name: String, val id: Int) : Filter.CheckBox(name)
    private class Rating(name: String, val id: Int) : Filter.CheckBox(name, state = true)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Завершенный", "Продолжающийся"))

    private class RatingList : Filter.Group<Rating>(
        "Возрастная категория",
        listOf(
            Rating("???", 1),
            Rating("0+", 2),
            Rating("6+", 3),
            Rating("12+", 4),
            Rating("16+", 5),
            Rating("18+", 6),
        ),
    )

    override fun getFilterList() = FilterList(
        Status(),
        RatingList(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("Животные", 1),
        Genre("Драма", 2),
        Genre("Фэнтези", 3),
        Genre("Игры", 4),
        Genre("Юмор", 5),
        Genre("Журнал", 6),
        Genre("Паранормальное", 7),
        Genre("Конец света", 8),
        Genre("Романтика", 9),
        Genre("Фантастика", 10),
        Genre("Бытовое", 11),
        Genre("Стимпанк", 12),
        Genre("Супергерои", 13),
    )
}
