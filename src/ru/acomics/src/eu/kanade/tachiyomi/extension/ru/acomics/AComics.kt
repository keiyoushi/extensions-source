package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AComics : ParsedHttpSource() {

    override val name = "AComics"

    override val baseUrl = "https://acomics.ru"

    override val lang = "ru"

    override val client = network.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val newReq = chain
                .request()
                .newBuilder()
                .addHeader("Cookie", "ageRestrict=17;")
                .build()

            chain.proceed(newReq)
        }.build()

    override val supportsLatest = true

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/comics?$DEFAULT_COMIC_QUERIES&sort=subscr_count&skip=${10 * (page - 1)}", headers)

    override fun popularMangaSelector() = "section.serial-card"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.selectFirst("a > img")?.absUrl("data-real-src")
        element.selectFirst("h2 > a")!!.run {
            setUrlWithoutDomain(attr("href") + "/about")
            title = text()
        }
    }

    override fun popularMangaNextPageSelector() = "a.infinite-scroll"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/comics?$DEFAULT_COMIC_QUERIES&sort=last_update&skip=${10 * (page - 1)}", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?keyword=$query"
        } else {
            val urlBuilder = "$baseUrl/comics?type=0&subscribe=0&issue_count=2&sort=subscr_count"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("skip", "${10 * (page - 1)}")
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        val categories = filter.state.filter { it.state }.joinToString(",") { it.id }
                        urlBuilder.addQueryParameter("categories", categories)
                    }
                    is Status -> {
                        val status = when (filter.state) {
                            1 -> "no"
                            2 -> "yes"
                            else -> "0"
                        }
                        urlBuilder.addQueryParameter("updatable", status)
                    }
                    is RatingList -> {
                        filter.state.forEach {
                            if (it.state) {
                                urlBuilder.addQueryParameter("ratings[]", it.id)
                            }
                        }
                    }
                    else -> {}
                }
            }
            urlBuilder.build().toString()
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val article = document.selectFirst("article.common-article")!!
        with(article) {
            title = selectFirst(".page-header-with-menu h1")!!.text()
            genre = select("p.serial-about-badges a.category").joinToString { it.text() }
            author = select("p.serial-about-authors a, p:contains(Автор оригинала)").joinToString { it.ownText() }
            description = selectFirst("section.serial-about-text")?.text()
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val count = doc
            .selectFirst("p:has(b:contains(Количество выпусков:))")!!
            .ownText()
            .toInt()

        val comicPath = doc.location().substringBefore("/about")

        return (count downTo 1).map {
            SChapter.create().apply {
                chapter_number = it.toFloat()
                name = it.toString()
                setUrlWithoutDomain("$comicPath/$it")
            }
        }
    }

    override fun chapterListSelector(): Nothing = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val imageElement = document.selectFirst("img.issue")!!
        return listOf(Page(0, imageUrl = imageElement.absUrl("src")))
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    // ============================== Filters ===============================
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class Rating(name: String, val id: String) : Filter.CheckBox(name, state = true)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Завершенный", "Продолжающийся"))

    private class GenreList : Filter.Group<Genre>(
        "Категории",
        listOf(
            Genre("Животные", "1"),
            Genre("Драма", "2"),
            Genre("Фэнтези", "3"),
            Genre("Игры", "4"),
            Genre("Юмор", "5"),
            Genre("Журнал", "6"),
            Genre("Паранормальное", "7"),
            Genre("Конец света", "8"),
            Genre("Романтика", "9"),
            Genre("Фантастика", "10"),
            Genre("Бытовое", "11"),
            Genre("Стимпанк", "12"),
            Genre("Супергерои", "13"),
        ),
    )

    private class RatingList : Filter.Group<Rating>(
        "Возрастная категория",
        listOf(
            Rating("???", "1"),
            Rating("0+", "2"),
            Rating("6+", "3"),
            Rating("12+", "4"),
            Rating("16+", "5"),
            Rating("18+", "6"),
        ),
    )

    override fun getFilterList() = FilterList(
        Status(),
        RatingList(),
        GenreList(),
    )
}

private const val DEFAULT_COMIC_QUERIES = "categories=&ratings[]=1&ratings[]=2&ratings[]=3&ratings[]=4&ratings[]=5&ratings[]=6&type=0&updatable=0&issue_count=2"
