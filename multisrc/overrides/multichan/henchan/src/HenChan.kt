package eu.kanade.tachiyomi.extension.ru.henchan

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HenChan : MultiChan("HenChan", "https://xxxxx.hentaichan.live", "ru"), ConfigurableSource {

    override val id = 5504588601186153612

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val domain = preferences.getString(DOMAIN_TITLE, DOMAIN_DEFAULT)!!

    override val baseUrl = domain

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/newest?offset=${20 * (page - 1)}")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/?do=search&subaction=search&story=$query&search_start=$page"
        } else {
            var genres = ""
            var order = ""
            filters.forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state
                            .filter { !it.isIgnored() }
                            .forEach { f ->
                                genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                            }
                    }

                    else -> return@forEach
                }
            }

            if (genres.isNotEmpty()) {
                filters.forEach { filter ->
                    when (filter) {
                        is OrderBy -> {
                            order = filter.toUriPartWithGenres()
                        }

                        else -> return@forEach
                    }
                }
                "$baseUrl/tags/${genres.dropLast(1)}&sort=manga$order?offset=${20 * (page - 1)}"
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is OrderBy -> {
                            order = filter.toUriPartWithoutGenres()
                        }

                        else -> return@forEach
                    }
                }
                "$baseUrl/$order?offset=${20 * (page - 1)}"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".content_row:not(:has(div.item:containsOwn(Тип)))"

    private fun String.getHQThumbnail(): String {
        val isExHenManga = this.contains("/manganew_thumbs_blur/")
        val regex = "(?<=/)manganew_thumbs\\w*?(?=/)".toRegex(RegexOption.IGNORE_CASE)
        return this.replace(regex, "showfull_retina/manga")
            .replace(
                "_".plus(URL(baseUrl).host),
                "_hentaichan.ru",
            ) // domain-related replacing for very old mangas
            .plus(
                if (isExHenManga) {
                    "#"
                } else {
                    ""
                },
            ) // # for later so we know what type manga is it
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = super.popularMangaFromElement(element)
        manga.thumbnail_url = element.select("img").first()!!.attr("src").getHQThumbnail()
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)
        manga.thumbnail_url = document.select("img#cover").attr("abs:src").getHQThumbnail()
        return manga
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    // Error message for exceeding last page
                    if (response.code == 404) {
                        Observable.just(
                            listOf(
                                SChapter.create().apply {
                                    url = manga.url
                                    name = "Chapter"
                                    chapter_number = 1f
                                },
                            ),
                        )
                    } else {
                        throw Exception("HTTP error ${response.code}")
                    }
                }
            }
            .map { response ->
                chapterListParse(response)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl + if (manga.thumbnail_url?.endsWith("#") == true) {
            manga.url
        } else {
            manga.url.replace("/manga/", "/related/")
        }
        return (GET(url, headers))
    }

    override fun chapterListSelector() = ".related"

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseUrl = response.request.url.toString()
        val document = response.asJsoup()

        // exhentai chapter
        if (responseUrl.contains("/manga/")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(responseUrl)
            chap.name = document.select("a.title_top_a").text()
            chap.chapter_number = 1F

            val date = document.select("div.row4_right b")?.text()?.let {
                SimpleDateFormat("dd MMMM yyyy", Locale("ru")).parse(it)?.time ?: 0
            } ?: 0
            chap.date_upload = date
            return listOf(chap)
        }

        // one chapter, nothing related
        if (document.select("#right > div:nth-child(4)").text().contains(" похожий на ")) {
            val chap = SChapter.create()
            chap.setUrlWithoutDomain(document.select("#left > div > a").attr("href"))
            chap.name = document.select("#right > div:nth-child(4)").text()
                .split(" похожий на ")[1]
                .replace("\\\"", "\"")
                .replace("\\'", "'")
            chap.chapter_number = 1F
            chap.date_upload =
                Date().time // setting to current date because of a sorting in the "Recent updates" section
            return listOf(chap)
        }

        // has related chapters
        val result = mutableListOf<SChapter>()
        result.addAll(
            document.select(chapterListSelector()).map {
                chapterFromElement(it)
            },
        )

        var url = document.select("div#pagination_related a:contains(Вперед)").attr("href")
        while (url.isNotBlank()) {
            val get = GET(
                "${response.request.url}/$url",
                headers = headers,
            )
            val nextPage = client.newCall(get).execute().asJsoup()
            result.addAll(
                nextPage.select(chapterListSelector()).map {
                    chapterFromElement(it)
                },
            )

            url = nextPage.select("div#pagination_related a:contains(Вперед)").attr("href")
        }

        return result.reversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("h2 a").attr("href"))
        val chapterName = element.select("h2 a").attr("title")
        chapter.name = chapterName
        chapter.chapter_number =
            "(глава\\s|часть\\s)([0-9]+\\.?[0-9]*)".toRegex(RegexOption.IGNORE_CASE)
                .find(chapterName)?.groupValues?.get(2)?.toFloat() ?: -1F
        chapter.date_upload =
            Date().time // setting to current date because of a sorting in the "Recent updates" section
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.contains("/manga/")) {
            baseUrl + chapter.url.replace("/manga/", "/online/")
        } else {
            baseUrl + chapter.url
        }
        return GET(url, Headers.Builder().add("Accept", "image/webp,image/apng").build())
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val prefix = "fullimg\": ["
        val beginIndex = html.indexOf(prefix) + prefix.length
        val endIndex = html.indexOf("]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex)
            .replace("\"", "")
            .replace("\'", "")

        val pageUrls = trimmedHtml.split(", ")
        return pageUrls.mapIndexed { i, url -> Page(i, "", url) }
    }

    private class Genre(
        val id: String,
        @SuppressLint("DefaultLocale") name: String = id.replace('_', ' ').capitalize(),
    ) : Filter.TriState(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class OrderBy : UriPartFilter(
        "Сортировка",
        arrayOf("Дата", "Популярность", "Алфавит"),
        arrayOf("&n=dateasc" to "", "&n=favasc" to "&n=favdesc", "&n=abcdesc" to "&n=abcasc"),
        arrayOf(
            "manga/new&n=dateasc" to "manga/new",
            "manga/new&n=favasc" to "mostfavorites&sort=manga",
            "manga/new&n=abcdesc" to "manga/new&n=abcasc",
        ),
    )

    private open class UriPartFilter(
        displayName: String,
        sortNames: Array<String>,
        val withGenres: Array<Pair<String, String>>,
        val withoutGenres: Array<Pair<String, String>>,
    ) :
        Filter.Sort(displayName, sortNames, Selection(1, false)) {
        fun toUriPartWithGenres() =
            if (state!!.ascending) withGenres[state!!.index].first else withGenres[state!!.index].second

        fun toUriPartWithoutGenres() =
            if (state!!.ascending) withoutGenres[state!!.index].first else withoutGenres[state!!.index].second
    }

    override fun getFilterList() = FilterList(
        OrderBy(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("3D"),
        Genre("action"),
        Genre("ahegao"),
        Genre("bdsm"),
        Genre("corruption"),
        Genre("foot_fetish"),
        Genre("footfuck"),
        Genre("gender_bender"),
        Genre("live"),
        Genre("lolcon"),
        Genre("megane"),
        Genre("mind_break"),
        Genre("monstergirl"),
        Genre("netorare"),
        Genre("netori"),
        Genre("nipple_penetration"),
        Genre("oyakodon"),
        Genre("paizuri_(titsfuck)"),
        Genre("rpg"),
        Genre("scat"),
        Genre("shemale"),
        Genre("shimaidon"),
        Genre("shooter"),
        Genre("simulation"),
        Genre("skinsuit"),
        Genre("tomboy"),
        Genre("tomgirl"),
        Genre("x-ray"),
        Genre("алкоголь"),
        Genre("анал"),
        Genre("андроид"),
        Genre("анилингус"),
        Genre("анимация"),
        Genre("аркада"),
        Genre("арт"),
        Genre("бабушка"),
        Genre("без_текста"),
        Genre("без_трусиков"),
        Genre("без_цензуры"),
        Genre("беременность"),
        Genre("бикини"),
        Genre("близнецы"),
        Genre("боди-арт"),
        Genre("больница"),
        Genre("большая_грудь"),
        Genre("большие_попки"),
        Genre("бондаж"),
        Genre("буккаке"),
        Genre("в_ванной"),
        Genre("в_общественном_месте"),
        Genre("в_первый_раз"),
        Genre("в_цвете"),
        Genre("в_школе"),
        Genre("вампиры"),
        Genre("веб"),
        Genre("вебкам"),
        Genre("вибратор"),
        Genre("визуальная_новелла"),
        Genre("внучка"),
        Genre("волосатые_женщины"),
        Genre("гаремник"),
        Genre("гг_девушка"),
        Genre("гг_парень"),
        Genre("гипноз"),
        Genre("глубокий_минет"),
        Genre("горячий_источник"),
        Genre("грудастая_лоли"),
        Genre("групповой_секс"),
        Genre("гяру_и_гангуро"),
        Genre("двойное_проникновение"),
        Genre("девочки_волшебницы"),
        Genre("девушка_туалет"),
        Genre("демоны"),
        Genre("дилдо"),
        Genre("дочь"),
        Genre("драма"),
        Genre("дыра_в_стене"),
        Genre("жестокость"),
        Genre("за_деньги"),
        Genre("зомби"),
        Genre("зрелые_женщины"),
        Genre("измена"),
        Genre("изнасилование"),
        Genre("инопланетяне"),
        Genre("инцест"),
        Genre("исполнение_желаний"),
        Genre("камера"),
        Genre("квест"),
        Genre("кимоно"),
        Genre("колготки"),
        Genre("комиксы"),
        Genre("косплей"),
        Genre("кремпай"),
        Genre("кудере"),
        Genre("кузина"),
        Genre("куннилингус"),
        Genre("купальники"),
        Genre("латекс_и_кожа"),
        Genre("магия"),
        Genre("маленькая_грудь"),
        Genre("мастурбация"),
        Genre("мать"),
        Genre("мейдочки"),
        Genre("мерзкий_дядька"),
        Genre("минет"),
        Genre("много_девушек"),
        Genre("молоко"),
        Genre("монашки"),
        Genre("монстры"),
        Genre("мочеиспускание"),
        Genre("мужская_озвучка"),
        Genre("мужчина_крепкого_телосложения"),
        Genre("мускулистые_женщины"),
        Genre("на_природе"),
        Genre("наблюдение"),
        Genre("непрямой_инцест"),
        Genre("новелла"),
        Genre("обмен_партнерами"),
        Genre("обмен_телами"),
        Genre("обычный_секс"),
        Genre("огромная_грудь"),
        Genre("огромный_член"),
        Genre("оплодотворение"),
        Genre("остановка_времени"),
        Genre("парень_пассив"),
        Genre("переодевание"),
        Genre("песочница"),
        Genre("племянница"),
        Genre("пляж"),
        Genre("подглядывание"),
        Genre("подчинение"),
        Genre("похищение"),
        Genre("презерватив"),
        Genre("принуждение"),
        Genre("прозрачная_одежда"),
        Genre("проникновение_в_матку"),
        Genre("психические_отклонения"),
        Genre("публично"),
        Genre("рабыни"),
        Genre("романтика"),
        Genre("сверхъестественное"),
        Genre("секс_игрушки"),
        Genre("сестра"),
        Genre("сетакон"),
        Genre("скрытный_секс"),
        Genre("спортивная_форма"),
        Genre("спящие"),
        Genre("страпон"),
        Genre("суккубы"),
        Genre("темнокожие"),
        Genre("тентакли"),
        Genre("толстушки"),
        Genre("трап"),
        Genre("тётя"),
        Genre("умеренная_жестокость"),
        Genre("учитель_и_ученик"),
        Genre("ушастые"),
        Genre("фантазии"),
        Genre("фантастика"),
        Genre("фемдом"),
        Genre("фестиваль"),
        Genre("фетиш"),
        Genre("фистинг"),
        Genre("фурри"),
        Genre("футанари"),
        Genre("футанари_имеет_парня"),
        Genre("фэнтези"),
        Genre("хоррор"),
        Genre("цундере"),
        Genre("чикан"),
        Genre("чирлидеры"),
        Genre("чулки"),
        Genre("школьная_форма"),
        Genre("школьники"),
        Genre("школьницы"),
        Genre("школьный_купальник"),
        Genre("щекотка"),
        Genre("эксгибиционизм"),
        Genre("эльфы"),
        Genre("эччи"),
        Genre("юмор"),
        Genre("юри"),
        Genre("яндере"),
        Genre("яой"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = DOMAIN_TITLE
            this.title = DOMAIN_TITLE
            summary = domain
            this.setDefaultValue(DOMAIN_DEFAULT)
            dialogTitle = DOMAIN_TITLE
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res =
                        preferences.edit().putString(DOMAIN_TITLE, newValue as String).commit()
                    Toast.makeText(
                        screen.context,
                        "Для смены домена необходимо перезапустить приложение с полной остановкой.",
                        Toast.LENGTH_LONG,
                    ).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val DOMAIN_TITLE = "Домен"
        private const val DOMAIN_DEFAULT = "https://xxxxx.hentaichan.live"
    }
}
