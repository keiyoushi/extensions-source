package eu.kanade.tachiyomi.extension.it.hentaifantasy

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
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.regex.Pattern

class HentaiFantasy : ParsedHttpSource() {
    override val name = "HentaiFantasy"

    override val baseUrl = "https://www.hentaifantasy.it/index.php"

    override val lang = "it"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    companion object {
        val pagesUrlPattern: Pattern by lazy {
            Pattern.compile("""\"url\":\"(.*?)\"""")
        }

        val dateFormat by lazy {
            SimpleDateFormat("yyyy.MM.dd")
        }
    }

    override fun popularMangaSelector() = "div.list > div.group > div.title > a"

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/most_downloaded/$page/", headers)

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text().trim()
        return manga
    }

    override fun popularMangaNextPageSelector() = "div.next > a.gbutton:contains(»):last-of-type"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/latest/$page/", headers)

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tags = mutableListOf<String>()
        val paths = mutableListOf<String>()
        for (filter in if (filters.isEmpty()) getFilterList() else filters) {
            when (filter) {
                is TagList ->
                    filter.state
                        .filter { it.state }
                        .map {
                            paths.add(it.name.lowercase().replace(" ", "_"))
                            it.id.toString()
                        }
                        .forEach { tags.add(it) }
                else -> {}
            }
        }

        val searchTags = tags.size > 0
        if (!searchTags && query.length < 3) {
            throw Exception("Inserisci almeno tre caratteri")
        }

        val form = FormBody.Builder().apply {
            if (!searchTags) {
                add("search", query)
            } else {
                tags.forEach {
                    add("tag[]", it)
                }
            }
        }

        val searchPath = if (!searchTags) {
            "search"
        } else if (paths.size == 1) {
            "tag/${paths[0]}/$page"
        } else {
            "search_tags"
        }

        return POST("$baseUrl/$searchPath", headers, form.build())
    }

    override fun searchMangaFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        document.select("div#tablelist > div.row").forEach { row ->
            when (row.select("div.cell > b").first()!!.text().trim()) {
                "Autore" -> manga.author = row.select("div.cell > a").text().trim()
                "Genere", "Tipo" -> row.select("div.cell > a > span.label").forEach {
                    genres.add(it.text().trim())
                }
                "Descrizione" -> manga.description = row.select("div.cell").last()!!.text().trim()
            }
        }
        manga.genre = genres.joinToString(", ")
        manga.status = SManga.UNKNOWN
        manga.thumbnail_url = document.select("div.thumbnail > img").attr("src")
        return manga
    }

    override fun mangaDetailsRequest(manga: SManga) = POST(baseUrl + manga.url, headers)

    override fun chapterListSelector() = "div.list > div.group div.element"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("div.title > a").let {
            chapter.setUrlWithoutDomain(it.attr("href"))
            chapter.name = it.text().trim()
        }
        chapter.date_upload = element.select("div.meta_r").first()?.ownText()?.substringAfterLast(", ")?.trim()?.let {
            parseChapterDate(it)
        } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return when (date) {
            "Oggi" -> {
                Calendar.getInstance().timeInMillis
            }
            "Ieri" -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -1)
                }.timeInMillis
            }
            else -> {
                try {
                    dateFormat.parse(date)?.time ?: 0L
                } catch (e: ParseException) {
                    0L
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter) = POST(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val pages = mutableListOf<Page>()

        val p = pagesUrlPattern
        val m = p.matcher(body)

        var i = 0
        while (m.find()) {
            pages.add(Page(i++, "", m.group(1)?.replace("""\\""", "")))
        }
        return pages
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlRequest(page: Page) = GET(page.url)

    override fun imageUrlParse(document: Document) = ""

    private class Tag(name: String, val id: Int) : Filter.CheckBox(name)
    private class TagList(title: String, tags: List<Tag>) : Filter.Group<Tag>(title, tags)

    override fun getFilterList() = FilterList(
        TagList("Generi", getTagList()),
    )

    // Tags: 47
    // $("select[name='tag[]']:eq(0) > option").map((i, el) => `Tag("${$(el).text().trim()}", ${$(el).attr("value")})`).get().sort().join(",\n")
    // on https://www.hentaifantasy.it/search/
    private fun getTagList() = listOf(
        Tag("Ahegao", 56),
        Tag("Anal", 28),
        Tag("Ashikoki", 12),
        Tag("Bestiality", 24),
        Tag("Bizzare", 44),
        Tag("Bondage", 30),
        Tag("Cheating", 33),
        Tag("Chubby", 57),
        Tag("Dark Skin", 39),
        Tag("Demon Girl", 43),
        Tag("Femdom", 38),
        Tag("Forced", 46),
        Tag("Full color", 52),
        Tag("Furry", 36),
        Tag("Futanari", 18),
        Tag("Group", 34),
        Tag("Guro", 8),
        Tag("Harem", 41),
        Tag("Housewife", 51),
        Tag("Incest", 11),
        Tag("Lolicon", 20),
        Tag("Maid", 55),
        Tag("Milf", 31),
        Tag("Monster Girl", 15),
        Tag("Nurse", 49),
        Tag("Oppai", 25),
        Tag("Paizuri", 42),
        Tag("Pettanko", 35),
        Tag("Pissing", 32),
        Tag("Public", 53),
        Tag("Rape", 21),
        Tag("Schoolgirl", 27),
        Tag("Shotacon", 26),
        Tag("Stockings", 40),
        Tag("Swimsuit", 47),
        Tag("Tanlines", 48),
        Tag("Teacher", 50),
        Tag("Tentacle", 23),
        Tag("Toys", 45),
        Tag("Trap", 29),
        Tag("Tsundere", 54),
        Tag("Uncensored", 59),
        Tag("Vanilla", 19),
        Tag("Yandere", 58),
        Tag("Yaoi", 22),
        Tag("Yuri", 14),
    )
}
