package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AComics : ParsedHttpSource() {

    override val name = "AComics"

    override val baseUrl = "https://acomics.ru"

    override val lang = "ru"

    override val client = network.cloudflareClient.newBuilder()
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
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", Sort.POPULAR)

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
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", Sort.LATEST)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = if (query.isNotEmpty()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
        } else {
            val categories = mutableListOf<String>()
            val ratings = mutableListOf<String>()
            var comicType = "0"
            var publication = "0"
            var subscription = "0"
            var minPages = "2"
            var sort = "subscr_count"

            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is Categories -> {
                        val selected = filter
                            .state
                            .filter { it.state }
                            .map { it.id }
                            .sorted()
                            .map { it.toString() }
                        categories.addAll(selected)
                    }
                    is Ratings -> {
                        val selected = filter
                            .state
                            .filter { it.state }
                            .map { it.id }
                            .sorted()
                            .map { it.toString() }
                        ratings.addAll(selected)
                    }

                    // ---

                    is ComicType -> {
                        comicType = when (filter.state) {
                            1 -> "orig"
                            2 -> "trans"
                            else -> comicType
                        }
                    }
                    is Publication -> {
                        publication = when (filter.state) {
                            1 -> "no"
                            2 -> "yes"
                            else -> publication
                        }
                    }
                    is Subscription -> {
                        subscription = when (filter.state) {
                            1 -> "yes"
                            2 -> "no"
                            else -> subscription
                        }
                    }
                    is MinPages -> {
                        minPages = filter.state
                            .toIntOrNull()
                            ?.toString()
                            ?: minPages
                    }
                    is Sort -> {
                        sort = when (filter.state) {
                            0 -> "last_update"
                            1 -> "subscr_count"
                            2 -> "issue_count"
                            3 -> "serial_name"
                            else -> sort
                        }
                    }
                    else -> {}
                }
            }

            "$baseUrl/comics".toHttpUrl().newBuilder()
                .addIndexedQueryParameters("categories", categories, page == 1)
                .addIndexedQueryParameters("ratings", ratings, page == 1)
                .addQueryParameter("type", comicType)
                .addQueryParameter("updatable", publication)
                .addQueryParameter("subscribe", subscription)
                .addQueryParameter("issue_count", minPages)
                .addQueryParameter("sort", sort)
        }

        if (page > 1) {
            urlBuilder.addQueryParameter("skip", ((page - 1) * 10).toString())
        }

        return GET(urlBuilder.build(), headers)
    }

    fun HttpUrl.Builder.addIndexedQueryParameters(
        name: String,
        values: Iterable<String?>,
        collapse: Boolean,
    ): HttpUrl.Builder = apply {
        values.forEachIndexed { i, value ->
            val key = if (collapse) "$name[]" else "$name[$i]"
            addQueryParameter(key, value)
        }
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
    private class Genre(name: String, val id: Int) : Filter.CheckBox(name)
    private class Rating(name: String, val id: Int) : Filter.CheckBox(name, state = true)

    private class Categories : Filter.Group<Genre>(
        "Категории",
        listOf(
            Genre("Животные", 1),
            Genre("Драма", 2),
            Genre("Фентези", 3),
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
            Genre("Детектив", 14),
            Genre("Историческое", 15),
        ),
    )

    private class Ratings : Filter.Group<Rating>(
        "Возрастная категория",
        listOf(
            Rating("NR", 1),
            Rating("G", 2),
            Rating("PG", 3),
            Rating("PG-13", 4),
            Rating("R", 5),
            Rating("NC-17", 6),
        ),
    )

    private class ComicType : Filter.Select<String>("Тип комикса", arrayOf("Все", "Оригинальный", "Перевод")) // "0", "orig", "trans"
    private class Publication : Filter.Select<String>("Публикация", arrayOf("Все", "Завершенный", "Продолжающийся")) // "0", "no", "yes"
    private class Subscription : Filter.Select<String>("Подписка", arrayOf("Все", "В моей ленте", "Кроме моей ленты")) // "0", "yes", "no"
    private class MinPages : Filter.Text("Минимум страниц", state = "2")
    private class Sort(state: Int = 1) : Filter.Select<String>("Сортировка", arrayOf("по дате обновления", "по количеству подписчиков", "по количеству выпусков", "по алфавиту"), state = state) { // "last_update", "subscr_count", "issue_count", "serial_name"
        companion object {
            val LATEST = FilterList(Ratings(), Sort(0))
            val POPULAR = FilterList(Ratings(), Sort(1))
        }
    }

    override fun getFilterList() = FilterList(
        Categories(),
        Ratings(),
        Filter.Separator(),
        ComicType(),
        Publication(),
        Subscription(),
        MinPages(),
        Sort(),
    )
}
