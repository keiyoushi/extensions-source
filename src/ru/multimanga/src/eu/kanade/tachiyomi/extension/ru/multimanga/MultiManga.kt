package eu.kanade.tachiyomi.extension.ru.multimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.lang.UnsupportedOperationException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MultiManga : ParsedHttpSource() {
    override val name = "MultiManga"
    override val baseUrl = "https://multi-manga.com"
    override val lang = "ru"
    override val supportsLatest = true
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    override val client = network.cloudflareClient

    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl + if (page > 1) {
            "/page/$page/"
        } else {
            ""
        }

        return GET(url, headers)
    }

    override fun popularMangaSelector() = ".index-popular .gallery"
    override fun latestUpdatesSelector() = ".index-container:not(.index-popular) .gallery"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = null
    override fun latestUpdatesNextPageSelector() = "a.next"
    override fun searchMangaNextPageSelector() = "a#nextlink, a.next"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
            title = element.select("a.cover div.caption").text()
            thumbnail_url = element.select("a.cover img").imgAttr()
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = filters.filterIsInstance<GenreListFilter>().first()
            .state.filter { it.state }.map { it.id }

        val url = if (query.isNotBlank() || genres.isEmpty()) {
            "$baseUrl/index.php?do=search&subaction=search&search_start=$page&full_search=0"
                .toHttpUrl().newBuilder()
                .addQueryParameter("story", query)
                .toString()
        } else {
            "$baseUrl/f/n.m.tags=${genres.joinToString(",")}/sort=date/order=desc" +
                if (page > 1) {
                    "/page/2/"
                } else {
                    ""
                }
        }

        return GET(url, headers)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("#info h1").text()
            author = document.select("a.tag[href*=/autor/]").eachText().joinToString()
            genre = document.select("a.tag[href*=/tags/]").eachText().joinToString()
            thumbnail_url = document.select("#cover img").imgAttr()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    override fun chapterListSelector() = "#content"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.ownerDocument()!!.location())
            name = "Chapter"
            date_upload = element.select("div:contains(Загружено) time")
                .attr("datetime").let { parseChapterDate(it) }
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("a.gallerythumb img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: text search will be ignored if genre's picked"),
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
}
