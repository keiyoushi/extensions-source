package eu.kanade.tachiyomi.extension.ru.yagamiproject

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class YagamiProject : ParsedHttpSource() {
    // Info
    override val name = "YagamiProject"
    override val baseUrl = "https://read.yagami.me"
    override val lang = "ru"
    override val supportsLatest = true
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/list-new/$page", headers)
    override fun popularMangaNextPageSelector() = ".panel_nav .button a"
    override fun popularMangaSelector() = ".list .group"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select(".title a").first()!!.let {
                setUrlWithoutDomain(it.attr("href"))
                val baseTitle = it.attr("title")
                title = if (baseTitle.isNullOrEmpty()) { it.text() } else baseTitle.split(" / ").min()
            }
            thumbnail_url = element.select(".cover_mini > img").attr("src").replace("thumb_", "")
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest/$page", headers)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/reader/search/?s=$query&p=$page", headers)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryList -> {
                    if (filter.state > 0) {
                        val catQ = getCategoryList()[filter.state].name
                        val catUrl = "$baseUrl/tags/$catQ".toHttpUrlOrNull()!!.newBuilder()
                        return GET(catUrl.toString(), headers)
                    }
                }
                is FormatList -> {
                    if (filter.state > 0) {
                        val formN = getFormatList()[filter.state].query
                        val formaUrl = "$baseUrl/$formN".toHttpUrlOrNull()!!.newBuilder()
                        return GET(formaUrl.toString(), headers)
                    }
                }
                else -> {}
            }
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select(".large.comic .info").first()!!
        val manga = SManga.create()
        val titlestr = document.select("title").text().substringBefore(" :: Yagami").split(" :: ").sorted()
        manga.title = titlestr.first().replace(":: ", "")
        manga.thumbnail_url = document.select(".cover img").first()!!.attr("src")
        manga.author = infoElement.select("li:contains(Автор(ы):)").first()?.text()?.substringAfter("Автор(ы): ")?.split(" / ")?.min()?.replace("N/A", "")?.trim()
        manga.artist = infoElement.select("li:contains(Художник(и):)").first()?.text()?.substringAfter("Художник(и): ")?.split(" / ")?.min()?.replace("N/A", "")?.trim()
        manga.status = when (infoElement.select("li:contains(Статус перевода:) span").first()?.text()) {
            "онгоинг" -> SManga.ONGOING
            "активный" -> SManga.ONGOING
            "завершён" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.genre = infoElement.select("li:contains(Жанры:)").first()?.text()?.substringAfter("Жанры: ")
        val altSelector = infoElement.select("li:contains(Название:)")
        var altName = ""
        if (altSelector.isNotEmpty()) {
            altName = "Альтернативные названия:\n" + altSelector.first().toString().replace("<li><b>Название</b>: ", "").replace("<br>", " / ").substringAfter(" / ").substringBefore("</li>") + "\n\n"
        }
        val descriptElem = infoElement.select("li:contains(Описание:)").first()?.text()?.substringAfter("Описание: ") ?: ""
        manga.description = titlestr.last().replace(":: ", "") + "\n" + altName + descriptElem
        return manga
    }

    // Chapters
    override fun chapterListSelector(): String = ".list .element"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapter = element.select(".title a")
        val chapterScanDate = element.select(".meta_r")

        name = if (chapter.attr("title").isNullOrBlank()) {
            chapter.text()
        } else {
            chapter.attr("title")
        }

        chapter_number = name.substringBefore(":").substringAfterLast(" ").substringAfterLast("№").substringAfterLast("#").toFloatOrNull() ?: chapter.attr("href").substringBeforeLast("/").substringAfterLast("/").toFloatOrNull() ?: -1f

        setUrlWithoutDomain(chapter.attr("href"))
        date_upload = parseDate(chapterScanDate.text().substringAfter(", "))
        scanlator = if (chapterScanDate.select("a").isNotEmpty()) {
            chapterScanDate.select("a").joinToString(" / ") { it.text() }
        } else {
            null
        }
    }
    private fun parseDate(date: String): Long {
        return when (date) {
            "Сегодня" -> System.currentTimeMillis()
            "Вчера" -> System.currentTimeMillis() - 24 * 60 * 60 * 1000
            else -> SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val defaultsel = document.select(".dropdown li a")
        val webtoonsel = document.select(".web_pictures img.web_img")

        if (webtoonsel.isNullOrEmpty()) {
            defaultsel.forEach {
                add(Page(it.text().substringAfter("Стр. ").toInt(), it.attr("href")))
            }
        } else {
            webtoonsel.mapIndexed { i, img ->
                add(Page(i, "", img.attr("src")))
            }
        }
    }
    override fun imageUrlParse(document: Document): String {
        val defaultimg = document.select("#page img").attr("src")
        return if (defaultimg.contains("string(1)")) {
            document.select("#get_download").first()!!.attr("href")
        } else {
            defaultimg
        }
    }

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("ПРИМЕЧАНИЕ: Фильтры исключают другдруга!"),
        CategoryList(categoriesName),
        FormatList(formasName),
    )

    private class FormatList(formas: Array<String>) : Filter.Select<String>("Тип", formas)
    private data class FormUnit(val name: String, val query: String)
    private val formasName = getFormatList().map {
        it.name
    }.toTypedArray()

    private fun getFormatList() = listOf(
        FormUnit("Все", "not"),
        FormUnit("Манга", "manga"),
        FormUnit("Манхва", "manhva"),
        FormUnit("Веб Манхва", "webtoon"),
        FormUnit("Маньхуа", "manhua"),
        FormUnit("Артбуки", "artbooks"),

    )

    private class CategoryList(categories: Array<String>) : Filter.Select<String>("Категории", categories)
    private data class CatUnit(val name: String)
    private val categoriesName = getCategoryList().map {
        it.name
    }.toTypedArray()

    private fun getCategoryList() = listOf(
        CatUnit("Без категории"),
        CatUnit("боевые искусства"),
        CatUnit("гарем"),
        CatUnit("гендерная интрига"),
        CatUnit("дзёсэй"),
        CatUnit("для взрослых"),
        CatUnit("драма"),
        CatUnit("зрелое"),
        CatUnit("исторический"),
        CatUnit("комедия"),
        CatUnit("меха"),
        CatUnit("мистика"),
        CatUnit("научная фантастика"),
        CatUnit("непристойности"),
        CatUnit("постапокалиптика"),
        CatUnit("повседневность"),
        CatUnit("приключения"),
        CatUnit("психология"),
        CatUnit("романтика"),
        CatUnit("сверхъестественное"),
        CatUnit("сёдзё"),
        CatUnit("сёнэн"),
        CatUnit("спорт"),
        CatUnit("сэйнэн"),
        CatUnit("трагедия"),
        CatUnit("ужасы"),
        CatUnit("фэнтези"),
        CatUnit("школьная жизнь"),
        CatUnit("экшн"),
        CatUnit("эротика"),
        CatUnit("этти"),
    )
}
