package eu.kanade.tachiyomi.extension.ru.multimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.text.SimpleDateFormat

class MultiManga : ParsedHttpSource() {
    override val name = "Multi-manga"
    override val baseUrl = "https://w1.multi-manga.com"
    override val lang = "ru"
    override val supportsLatest = true
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    // similar/modified theme of "https://komikindo.id"

    // Formerly "Bacakomik" -> now "BacaKomik"
    // override val id = 4383360263234319058

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(12, 3)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl", headers)
    }

    override fun popularMangaSelector() = ".index-popular .gallery"
    override fun latestUpdatesSelector() = ".index-container > #dle-content > .gallery"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        manga.title = element.select("a.cover div.caption").text()
        manga.thumbnail_url = element.select("a.cover img").imgAttr()

        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="

        val genres = mutableListOf<String>()
        println("multi-manga: filters: $filters")
        var needToBeFiltered = false
        filters.forEach { filter ->
            when (filter) {
                is GenreListFilter -> {
                    println("multi-manga: GenreListFilter: ${filter.state}")
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { genres.add(it.id) }
                    needToBeFiltered = if (filter.state.filter { it.state != Filter.TriState.STATE_IGNORE }.isNotEmpty()) true else false
//                    filter.state
//                        .filter { it.state != Filter.TriState.STATE_IGNORE }
//                        .forEach { url.addQueryParameter("genre[]", it.id) }
                    println(
                        "multi-manga: filter-list: ${filter.state
                            .filter { it.state != Filter.TriState.STATE_IGNORE }}",
                    )
                }
                else -> {}
            }
        }

        val genreStr = genres.joinToString(",")

        val builtUrl = if (needToBeFiltered == false) "$baseUrl/index.php?do=search&subaction=search&search_start=$page&full_search=0" else "$baseUrl/f/n.m.tags=$genreStr/sort=date/order=desc"
        val url = builtUrl.toHttpUrl().newBuilder()
        println("multi-manga: builtURL: $builtUrl")
        println("multi-manga: needtoBeFiltered: $needToBeFiltered")
        url.addQueryParameter("story", query)
//        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("div#info-block #info h1").text()
        manga.author = document.select("section#tags > div:contains(Автор) > span.tags").text()
        val genres = mutableListOf<String>()
        document.select("div:contains(Теги) > span.tags > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.thumbnail_url = document.select("#cover > a > img").imgAttr()
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("berjalan") -> SManga.ONGOING
        element.lowercase().contains("tamat") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#content"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("#thumbnail-container .thumb-container a").first()!!
//        val chapter = SChapter.create()
//        chapter.setUrlWithoutDomain(urlElement.attr("href"))
//        chapter.name = urlElement.text()

        val chapter = SChapter.create()
        val loopbackUrl = urlElement.attr("href").removeRange(urlElement.attr("href").length - 3, urlElement.attr("href").length)
        println("multi-manga: loopbackUrl: $loopbackUrl")
        chapter.setUrlWithoutDomain(loopbackUrl)
        chapter.name = "Oneshot"
        chapter.date_upload = element.select("div:contains(Загружено) time").attr("datetime").let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
//        return if (date.contains("yang lalu")) {
//            val value = date.split(' ')[0].toInt()
//            when {
//                "detik" in date -> Calendar.getInstance().apply {
//                    add(Calendar.SECOND, value * -1)
//                }.timeInMillis
//                "menit" in date -> Calendar.getInstance().apply {
//                    add(Calendar.MINUTE, value * -1)
//                }.timeInMillis
//                "jam" in date -> Calendar.getInstance().apply {
//                    add(Calendar.HOUR_OF_DAY, value * -1)
//                }.timeInMillis
//                "hari" in date -> Calendar.getInstance().apply {
//                    add(Calendar.DATE, value * -1)
//                }.timeInMillis
//                "minggu" in date -> Calendar.getInstance().apply {
//                    add(Calendar.DATE, value * 7 * -1)
//                }.timeInMillis
//                "bulan" in date -> Calendar.getInstance().apply {
//                    add(Calendar.MONTH, value * -1)
//                }.timeInMillis
//                "tahun" in date -> Calendar.getInstance().apply {
//                    add(Calendar.YEAR, value * -1)
//                }.timeInMillis
//                else -> {
//                    0L
//                }
//            }
//        } else {
//            try {
//                dateFormat.parse(date)?.time ?: 0
//            } catch (_: Exception) {
//                0L
//            }
//        }
        return dateFormat.parse(date).time ?: 0
        println("multi-manga: date parsed: " + dateFormat.parse(date).time ?: 0)
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
//        println("multi-manga: Parse image: document " + document)
        val pages = mutableListOf<Page>()
        var i = 0
//        document.select("div#thumbnail-container > div.thumb-container > a.gallerythumb > img").filter { element ->
//            val parent = element.parent()
//            parent != null && parent.tagName() != "noscript"
//        }.forEach { element ->
//            val url = element.attr("onError").substringAfter("src='").substringBefore("';")
//            i++
//            if (url.isNotEmpty()) {
//                pages.add(Page(i, "", url))
//            }
//        }

//        var imageUrlStart = document.select("div#dle-content div#page-container section#image-container a > img").attr("src")
//        val startFrom = imageUrlStart.length - imageUrlStart.locate(".webp") + 1
//        val pageRange = 1.rangeTo(document.select(span.num - pages).text())
        document.select("div#thumbnail-container > div.thumb-container").forEach { element ->
            val url = element.select("a > img").attr("data-src")
            println("image added: " + url)
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }

        println("multi-manga: pages-list " + pages)
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        println("request initialised")
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Exclude doesn't work, and text search will be ignored if genre's picked"),
        Filter.Separator(),
        GenreListFilter(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("3D", "3D"),
        Genre("action", "action"),
        Genre("ahegao", "ahegao"),
        Genre("bdsm", "bdsm"),
        Genre("corruption", "corruption"),
        Genre("foot fetish", "foot+fetish"),
        Genre("footfuck", "footfuck"),
        Genre("gender bender", "gender+bender"),
        Genre("live", "live"),
        Genre("lolcon", "lolcon"),
        Genre("megane", "megane"),
        Genre("mind break", "mind+break"),
        Genre("monstergirl", "monstergirl"),
        Genre("netorare", "netorare"),
        Genre("netori", "netori"),
        Genre("nipple penetration", "nipple+penetration"),
        Genre("oyakodon", "oyakodon"),
        Genre("paizuri (titsfuck)", "paizuri"),
        Genre("rpg", "rpg"),
        Genre("scat", "scat"),
        Genre("shemale", "shemale"),
        Genre("shimaidon", "shimaidon"),
        Genre("shooter", "shooter"),
        Genre("simulation", "simulation"),
        Genre("skinsuit", "skinsuit"),
        Genre("tomboy", "tomboy"),
        Genre("tomgirl", "tomgirl"),
        Genre("x-ray", "x-ray"),
        Genre("алкоголь", "алкоголь"),
        Genre("анал", "анал"),
        Genre("андроид", "андроид"),
        Genre("анилингус", "анилингус"),
        Genre("анимация", "анимация"),
        Genre("аркада", "аркада"),
        Genre("арт", "арт"),
        Genre("бабушка", "бабушка"),
        Genre("без текста", "без+текста"),
        Genre("без трусиков", "без+трусиков"),
        Genre("без цензуры", "без+цензуры"),
        Genre("беременность", "беременность"),
        Genre("бикини", "бикини"),
        Genre("близнецы", "близнецы"),
        Genre("боди-арт", "боди-арт"),
        Genre("больница", "больница"),
        Genre("большая грудь", "большая+грудь"),
        Genre("большие попки", "большие+попки"),
        Genre("бондаж", "бондаж"),
        Genre("буккаке", "буккаке"),
        Genre("в ванной", "в+ванной"),
        Genre("в общественном месте", "в+общественном+месте"),
        Genre("в первый раз", "в+первый+раз"),
        Genre("в цвете", "в+цвете"),
        Genre("в школе", "в+школе"),
        Genre("вампиры", "вампиры"),
        Genre("веб", "веб"),
        Genre("вебкам", "вебкам"),
        Genre("вибратор", "вибратор"),
        Genre("визуальная новелла", "визуальная+новелла"),
        Genre("внучка", "внучка"),
        Genre("волосатые женщины", "волосатые+женщины"),
        Genre("гаремник", "гаремник"),
        Genre("гг девушка", "гг+девушка"),
        Genre("гг парень", "гг+парень"),
        Genre("гипноз", "гипноз"),
        Genre("глубокий минет", "глубокий+минет"),
        Genre("горячий источник", "горячий+источник"),
        Genre("грудастая лоли", "грудастая+лоли"),
        Genre("групповой секс", "групповой+секс"),
        Genre("гяру и гангуро", "гяру+и+гангуро"),
        Genre("двойное проникновение", "двойное+проникновение"),
        Genre("девочки волшебницы", "девочки+волшебницы"),
        Genre("девушка туалет", "девушка+туалет"),
        Genre("демоны", "демоны"),
        Genre("дилдо", "дилдо"),
        Genre("дочь", "дочь"),
        Genre("драма", "драма"),
        Genre("дыра в стене", "дыра+в+стене"),
        Genre("жестокость", "жестокость"),
        Genre("за деньги", "за+деньги"),
        Genre("зомби", "зомби"),
        Genre("зрелые женщины", "зрелые+женщины"),
        Genre("измена", "измена"),
        Genre("изнасилование", "изнасилование"),
        Genre("имеют парня", "имеют+парня"),
        Genre("инопланетяне", "инопланетяне"),
        Genre("инсеки", "инсеки"),
        Genre("инцест", "инцест"),
        Genre("исполнение желаний", "исполнение+желаний"),
        Genre("камера", "камера"),
        Genre("квест", "квест"),
        Genre("кимоно", "кимоно"),
        Genre("колготки", "колготки"),
        Genre("комиксы", "комиксы"),
        Genre("косплей", "косплей"),
        Genre("кремпай", "кремпай"),
        Genre("кудере", "кудере"),
        Genre("кузина", "кузина"),
        Genre("куннилингус", "куннилингус"),
        Genre("купальники", "купальники"),
        Genre("латекс и кожа", "латекс+и+кожа"),
        Genre("магия", "магия"),
        Genre("маленькая грудь", "маленькая+грудь"),
        Genre("мастурбация", "мастурбация"),
        Genre("мать", "мать"),
        Genre("мейдочки", "мейдочки"),
        Genre("мерзкий дядька", "мерзкий+дядька"),
        Genre("минет", "минет"),
        Genre("много девушек", "много+девушек"),
        Genre("молоко", "молоко"),
        Genre("монашки", "монашки"),
        Genre("монстры", "монстры"),
        Genre("мочеиспускание", "мочеиспускание"),
        Genre("мужская озвучка", "мужская+озвучка"),
        Genre("мужчина крепкого телосложения", "мужчина+крепкого+телосложения"),
        Genre("мускулистые женщины", "мускулистые+женщины"),
        Genre("на природе", "на+природе"),
        Genre("наблюдение", "наблюдение"),
        Genre("непрямой инцест", "непрямой+инцест"),
        Genre("новелла", "новелла"),
        Genre("обмен партнерами", "обмен+партнерами"),
        Genre("обмен телами", "обмен+телами"),
        Genre("обычный секс", "обычный+секс"),
        Genre("огромная грудь", "огромная+грудь"),
        Genre("огромный член", "огромный+член"),
        Genre("оплодотворение", "оплодотворение"),
        Genre("остановка времени", "остановка+времени"),
        Genre("парень пассив", "парень+пассив"),
        Genre("переодевание", "переодевание"),
        Genre("песочница", "песочница"),
        Genre("племянница", "племянница"),
        Genre("пляж", "пляж"),
        Genre("подглядывание", "подглядывание"),
        Genre("подчинение", "подчинение"),
        Genre("похищение", "похищение"),
        Genre("презерватив", "презерватив"),
        Genre("принуждение", "принуждение"),
        Genre("прозрачная одежда", "прозрачная+одежда"),
        Genre("проникновение в матку", "проникновение+в+матку"),
        Genre("психические отклонения", "психические+отклонения"),
        Genre("публично", "публично"),
        Genre("рабыни", "рабыни"),
        Genre("романтика", "романтика"),
        Genre("сверхъестественное", "сверхъестественное"),
        Genre("секс игрушки", "секс+игрушки"),
        Genre("сестра", "сестра"),
        Genre("сетакон", "сетакон"),
        Genre("скрытный секс", "скрытный+секс"),
        Genre("спортивная форма", "спортивная+форма"),
        Genre("спящие", "спящие"),
        Genre("страпон", "страпон"),
        Genre("суккубы", "суккубы"),
        Genre("темнокожие", "темнокожие"),
        Genre("тентакли", "тентакли"),
        Genre("толстушки", "толстушки"),
        Genre("трап", "трап"),
        Genre("тётя", "тётя"),
        Genre("умеренная жестокость", "умеренная+жестокость"),
        Genre("учитель и ученик", "учитель+и+ученик"),
        Genre("ушастые", "ушастые"),
        Genre("фантазии", "фантазии"),
        Genre("фантастика", "фантастика"),
        Genre("фемдом", "фемдом"),
        Genre("фестиваль", "фестиваль"),
        Genre("фетиш", "фетиш"),
        Genre("фистинг", "фистинг"),
        Genre("фурри", "фурри"),
        Genre("футанари", "футанари"),
        Genre("футанари имеет парня", "футанари+имеет+парня"),
        Genre("фэнтези", "фэнтези"),
        Genre("хоррор", "хоррор"),
        Genre("цундере", "цундере"),
        Genre("чикан", "чикан"),
        Genre("чирлидеры", "чирлидеры"),
        Genre("чулки", "чулки"),
        Genre("школьная форма", "школьная+форма"),
        Genre("школьники", "школьники"),
        Genre("школьницы", "школьницы"),
        Genre("школьный купальник", "школьный+купальник"),
        Genre("щекотка", "щекотка"),
        Genre("эксгибиционизм", "эксгибиционизм"),
        Genre("эльфы", "эльфы"),
        Genre("эччи", "эччи"),
        Genre("юмор", "юмор"),
        Genre("юри", "юри"),
        Genre("яндере", "яндере"),
        Genre("яой", "яой"),
    )

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    private fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
