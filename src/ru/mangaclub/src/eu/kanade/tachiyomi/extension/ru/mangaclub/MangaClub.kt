package eu.kanade.tachiyomi.extension.ru.mangaclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaClub : ParsedHttpSource() {

    /** Info **/
    override val name: String = "MangaClub"
    override val baseUrl: String = "https://mangaclub.ru"
    override val lang: String = "ru"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient

    /** Popular **/
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/f/sort=rating/order=desc/page/$page/", headers)
    override fun popularMangaNextPageSelector(): String = "div.pagination-list i.icon-right-open"
    override fun popularMangaSelector(): String = "div.shortstory"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("div.content-block div.image img").attr("abs:src")
        element.select("div.content-title h4.title a").apply {
            title = this.text().replace("\\'", "'").substringBefore("/").trim()
            setUrlWithoutDomain(this.attr("abs:href"))
        }
    }

    /** Latest **/
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/", headers)
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    /** Search **/
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var url = baseUrl
        if (query.isNotEmpty()) {
            val formData = FormBody.Builder()
                .add("do", "search")
                .add("subaction", "search")
                .add("search_start", "$page")
                .add("full_search", "0")
                .add("result_from", "${((page - 1) * 8) + 1}")
                .add("story", query).build()
            val requestHeaders = headers.newBuilder()
                .add("Content-Type", "application/x-www-form-urlencoded").build()
            return POST("$url/index.php?do=search", requestHeaders, formData)
        } else {
            url += "/f"
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        val genresIDs = mutableListOf<String>()
                        filter.state.forEach { genre -> if (genre.state) genresIDs += genre.id }
                        if (genresIDs.isNotEmpty()) url += "/n.l.tags=${genresIDs.joinToString(",")}"
                    }
                    is CategoryList -> {
                        val categoriesIDs = mutableListOf<String>()
                        filter.state.forEach { category -> if (category.state) categoriesIDs += category.id }
                        if (categoriesIDs.isNotEmpty()) url += "/o.cat=${categoriesIDs.joinToString(",")}"
                    }
                    is Status -> {
                        val statusID = arrayOf("Не выбрано", "Завершен", "Продолжается", "Заморожено/Заброшено")[filter.state]
                        if (filter.state > 0) url += "/status_translation=$statusID"
                    }
                    is OrderBy -> {
                        val orderState = if (filter.state!!.ascending) "asc" else "desc"
                        val orderID = arrayOf("date", "editdate", "title", "comm_num", "news_read", "rating")[filter.state!!.index]
                        url += "/sort=$orderID/order=$orderState"
                    }
                    else -> {}
                }
            }
            url += "/page/$page"
        }
        return GET(url, headers)
    }
    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    /** Details **/
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.select("div.image img").attr("abs:src")
        title = document.select("div.info strong").text().replace("\\'", "'").substringBefore("/").trim()
        author = document.select("div.info a[href*=author]").joinToString(", ") { it.text().trim() }
        artist = author
        status = if (document.select("div.fullstory").text().contains("Данное произведение лицензировано на территории РФ. Главы удалены.")) SManga.LICENSED else when (document.select("div.info a[href*=status_translation]").text().trim()) {
            "Продолжается" -> SManga.ONGOING
            "Завершен" -> SManga.COMPLETED
            "Заморожено/Заброшено" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }

        description = document.select(".description").first()!!.text()
        genre = document.select("div.info a[href*=tags]").joinToString(", ") {
            it.text().replaceFirstChar { it.uppercase() }.trim()
        }
    }

    /** Chapters **/
    private val dateParse = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT)
    override fun chapterListSelector(): String = "div.chapters div.chapter-item"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterLink = element.select("div.chapter-item div.item-left a")
        name = chapterLink.text().replace(",", ".").trim()
        chapter_number = name.substringAfter("Глава").trim().toFloat()
        date_upload = element.select("div.chapter-item div.item-right div.date").text().trim().let { dateParse.parse(it)?.time ?: 0L }
        setUrlWithoutDomain(chapterLink.attr("abs:href"))
    }

    /** Pages **/
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select(".manga-lines-page a").forEach {
            add(Page(it.attr("data-p").toInt(), "", it.attr("data-i")))
        }
    }
    override fun imageUrlParse(document: Document): String = ""

    /** Filters **/
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class CategoryList(categories: List<Category>) : Filter.Group<Category>("Категория", categories)
    private class Category(name: String, val id: String) : Filter.CheckBox(name)
    private class Status : Filter.Select<String>(
        "Статус",
        arrayOf("Не выбрано", "Завершен", "Продолжается", "Заморожено/Заброшено"),
    )
    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("По дате добавления", "По дате обновления", "В алфавитном порядке", "По количеству комментариев", "По количеству просмотров", "По рейтингу"),
        Selection(5, false),
    )

    override fun getFilterList() = FilterList(
        GenreList(getGenreList()),
        Status(),
        CategoryList(getCategoryList()),
        OrderBy(),
    )

    private fun getCategoryList() = listOf(
        Category("Манга", "1"),
        Category("Манхва", "2"),
        Category("Маньхуа", "3"),
        Category("Веб-манхва", "6"),
    )

    private fun getGenreList() = listOf(
        Genre("Боевик", "боевик"),
        Genre("Боевые искусства", "боевые+искусства"),
        Genre("Вампиры", "вампиры"),
        Genre("Гарем", "гарем"),
        Genre("Гендерная интрига", "гендерная+интрига"),
        Genre("Героическое фэнтези", "героическое+фэнтези"),
        Genre("Детектив", "детектив"),
        Genre("Дзёсэй", "дзёсэй"),
        Genre("Додзинси", "додзинси"),
        Genre("Драма", "драма"),
        Genre("Игра", "игра"),
        Genre("История", "история"),
        Genre("Киберпанк", "киберпанк"),
        Genre("Комедия", "комедия"),
        Genre("Махо-сёдзё", "махо-сёдзё"),
        Genre("Меха", "меха"),
        Genre("Мистика", "мистика"),
        Genre("Музыка", "музыка"),
        Genre("Научная фантастика", "научная+фантастика"),
        Genre("Перерождение", "перерождение"),
        Genre("Повседневность", "повседневность"),
        Genre("Постапокалиптика", "постапокалиптика"),
        Genre("Приключения", "приключения"),
        Genre("Психология", "психология"),
        Genre("Романтика", "романтика"),
        Genre("Самурайский боевик", "самурайский+боевик"),
        Genre("Сборник", "сборник"),
        Genre("Сверхъестественное", "сверхъестественное"),
        Genre("Сингл", "сингл"),
        Genre("Спорт", "спорт"),
        Genre("Сэйнэн", "сэйнэн"),
        Genre("Сёдзё", "сёдзё"),
        Genre("Сёдзё для взрослых", "сёдзе+для+взрослых"),
        Genre("Сёдзё-ай", "сёдзё-ай"),
        Genre("Сёнэн", "сёнэн"),
        Genre("Сёнэн-ай", "сёнэн-ай"),
        Genre("Трагедия", "трагедия"),
        Genre("Триллер", "триллер"),
        Genre("Ужасы", "ужасы"),
        Genre("Фантастика", "фантастика"),
        Genre("Фэнтези", "фэнтези"),
        Genre("Школа", "школа"),
        Genre("Эротика", "эротика"),
        Genre("Ёнкома", "ёнкома"),
        Genre("Этти", "этти"),
        Genre("Юри", "юри"),
        Genre("Яой", "яой"),
    )
}
