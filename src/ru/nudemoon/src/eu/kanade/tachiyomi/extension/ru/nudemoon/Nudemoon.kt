package eu.kanade.tachiyomi.extension.ru.nudemoon

import android.webkit.CookieManager
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
import java.text.SimpleDateFormat
import java.util.Locale

class Nudemoon : ParsedHttpSource() {

    override val name = "Nude-Moon"

    override val baseUrl = "https://x.nude-moon.fun"

    override val lang = "ru"

    override val supportsLatest = true

    private val dateParseRu = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
    private val dateParseSlash = SimpleDateFormat("d/MM/yyyy", Locale("ru"))

    private val cookieManager by lazy { CookieManager.getInstance() }

    init {
        cookieManager.setCookie(baseUrl, "nm_mobile=1; Domain=" + baseUrl.split("//")[1])
    }

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()
        cookies["NMfYa"] = "1"
        cookies["nm_mobile"] = "1"
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
                .addHeader("Referer", baseUrl)
                .build()

            chain.proceed(newReq)
        }.build()

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/all_manga?views&rowstart=${30 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/all_manga?date&rowstart=${30 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Search by query on this site works really badly, i don't even sure of the need to implement it
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search?stext=${URLEncoder.encode(query, "CP1251")}&rowstart=${30 * (page - 1)}"
        } else {
            var genres = ""
            var order = ""
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                if (filter is GenreList) {
                    filter.state.forEach { f ->
                        if (f.state) {
                            genres += f.id + '+'
                        }
                    }
                }
            }

            if (genres.isNotEmpty()) {
                for (filter in filters) {
                    if (filter is OrderBy) {
                        // The site has no ascending order
                        order = arrayOf("&date", "&views", "&like")[filter.state!!.index]
                    }
                }
                "$baseUrl/tags/${genres.dropLast(1)}$order&rowstart=${30 * (page - 1)}"
            } else {
                for (filter in filters) {
                    if (filter is OrderBy) {
                        // The site has no ascending order
                        order = arrayOf(
                            "all_manga?date",
                            "all_manga?views",
                            "all_manga?like",
                        )[filter.state!!.index]
                    }
                }
                "$baseUrl/$order&rowstart=${30 * (page - 1)}"
            }
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "table.news_pic2"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = element.select("img.news_pic2").attr("abs:src")
        element.select("a:has(h2)").let {
            manga.title = it.text().substringBefore(" / ").substringBefore(" №")
            manga.setUrlWithoutDomain(it.attr("href"))
        }

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.small:contains(>)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("table.news_pic2").first()!!
        manga.title = document.select("h1").first()!!.text().substringBefore(" / ").substringBefore(" №")
        manga.author = infoElement.select("a[href*=mangaka]").text()
        manga.genre = infoElement.select("div.tag-links a").joinToString { it.text() }
        manga.description = document.select(".description").text()
        manga.thumbnail_url = document.selectFirst("meta[property=og:image]")!!.attr("abs:content")

        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url, headers)
    }

    override fun chapterListSelector() = popularMangaSelector()

    override fun chapterListParse(response: Response): List<SChapter> = mutableListOf<SChapter>().apply {
        val document = response.asJsoup()

        val allPageElement = document.select("td.button a:contains(Все главы)")

        if (allPageElement.isEmpty()) {
            add(
                SChapter.create().apply {
                    val chapterName = document.select("table td.bg_style1 h1").text()
                    val chapterUrl = response.request.url.toString()
                    setUrlWithoutDomain(chapterUrl)
                    name = "$chapterName Сингл"
                    scanlator = document.select("table.news_pic2 a[href*=perevod]").text()
                    date_upload = document.select("table.news_pic2 span.small2:contains(/)").text().let {
                        try {
                            dateParseSlash.parse(it)?.time ?: 0L
                        } catch (e: Exception) {
                            0
                        }
                    }
                    chapter_number = 0F
                },
            )
        } else {
            var pageListDocument: Document
            val pageListLink = allPageElement.attr("href")
            client.newCall(
                GET(baseUrl + pageListLink, headers),
            ).execute().run {
                if (!isSuccessful) {
                    close()
                    throw Exception("HTTP error $code")
                }
                pageListDocument = this.asJsoup()
            }
            pageListDocument.select(chapterListSelector())
                .forEach {
                    add(chapterFromElement(it))
                }
        }
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val nameAndUrl = element.select("tr[valign=top] a:has(h2)")
        name = nameAndUrl.select("h2").text()
        setUrlWithoutDomain(nameAndUrl.attr("abs:href"))
        if (url.contains(baseUrl)) {
            url = url.replace(baseUrl, "")
        }
        val informBlock = element.select("tr[valign=top] td[align=left]")
        scanlator = informBlock.select("a[href*=perevod]").text()
        date_upload = informBlock.select("span.small2")
            .text().replace("Май", "Мая").let { textDate ->
                try {
                    dateParseRu.parse(textDate)?.time ?: 0L
                } catch (e: Exception) {
                    0
                }
            }
        chapter_number = name.substringAfter("№").substringBefore(" ").toFloatOrNull() ?: -1f
    }

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        response.asJsoup().select("div.gallery-item img.textbox").mapIndexed { index, img ->
            add(Page(index, imageUrl = img.attr("abs:data-src")))
        }
        if (size == 0 && cookieManager.getCookie(baseUrl).contains("fusion_user").not()) {
            throw Exception("Страницы не найдены. Возможно необходима авторизация в WebView")
        }
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")

    private class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.CheckBox(name.replaceFirstChar { it.uppercaseChar() })
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Дата", "Просмотры", "Лайки"),
        Selection(1, false),
    )

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("анал"),
        Genre("без цензуры"),
        Genre("беременные"),
        Genre("близняшки"),
        Genre("большие груди"),
        Genre("в бассейне"),
        Genre("в больнице"),
        Genre("в ванной"),
        Genre("в общественном месте"),
        Genre("в первый раз"),
        Genre("в транспорте"),
        Genre("в туалете"),
        Genre("гарем"),
        Genre("гипноз"),
        Genre("горничные"),
        Genre("горячий источник"),
        Genre("групповой секс"),
        Genre("драма"),
        Genre("запредельное"),
        Genre("золотой дождь"),
        Genre("зрелые женщины"),
        Genre("идолы"),
        Genre("извращение"),
        Genre("измена"),
        Genre("имеют парня"),
        Genre("клизма"),
        Genre("колготки"),
        Genre("комиксы"),
        Genre("комиксы 3D"),
        Genre("косплей"),
        Genre("мастурбация"),
        Genre("мерзкий мужик"),
        Genre("много спермы"),
        Genre("молоко"),
        Genre("монстры"),
        Genre("на камеру"),
        Genre("на природе"),
        Genre("обычный секс"),
        Genre("огромный член"),
        Genre("пляж"),
        Genre("подглядывание"),
        Genre("принуждение"),
        Genre("продажность"),
        Genre("пьяные"),
        Genre("рабыни"),
        Genre("романтика"),
        Genre("с ушками"),
        Genre("секс игрушки"),
        Genre("спящие"),
        Genre("страпон"),
        Genre("студенты"),
        Genre("суккуб"),
        Genre("тентакли"),
        Genre("толстушки"),
        Genre("трапы"),
        Genre("ужасы"),
        Genre("униформа"),
        Genre("учитель и ученик"),
        Genre("фемдом"),
        Genre("фетиш"),
        Genre("фурри"),
        Genre("футанари"),
        Genre("футфетиш"),
        Genre("фэнтези"),
        Genre("цветная"),
        Genre("чикан"),
        Genre("чулки"),
        Genre("шимейл"),
        Genre("эксгибиционизм"),
        Genre("юмор"),
        Genre("юри"),
        Genre("ahegao"),
        Genre("BDSM"),
        Genre("ganguro"),
        Genre("gender bender"),
        Genre("megane"),
        Genre("mind break"),
        Genre("monstergirl"),
        Genre("netorare"),
        Genre("nipple penetration"),
        Genre("titsfuck"),
        Genre("x-ray"),
    )
}
