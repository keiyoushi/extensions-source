package eu.kanade.tachiyomi.extension.en.nineanime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NineAnime : ParsedHttpSource() {

    override val name = "NineAnime"

    override val baseUrl = "https://www.nineanime.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .followRedirects(true)
        .build()

    companion object {
        private const val PAGES_URL = "https://www.gardenhomefuture.com"
    }

    // not necessary for normal usage but added in an attempt to fix usage with VPN (see #3476)
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/77")
        .add("Accept-Language", "en-US,en;q=0.5")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category/index_$page.html?sort=views", headers)
    }

    override fun popularMangaSelector() = "div.post"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("p.title a").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a.next"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/category/index_$page.html?sort=updated", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search/?name=$query&page=$page.html", headers)
        } else {
            var url = "$baseUrl/category/"
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreFilter -> url += filter.toUriPart() + "_$page.html"
                    else -> {}
                }
            }
            GET(url, headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            with(document.select("div.manga-detailtop")) {
                thumbnail_url = select("img.detail-cover").attr("abs:src")
                author = select("span:contains(Author) + a").joinToString { it.text() }
                artist = select("span:contains(Artist) + a").joinToString { it.text() }
                status = when (select("p:has(span:contains(Status))").firstOrNull()?.ownText()) {
                    "Ongoing" -> SManga.ONGOING
                    "Completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
            with(document.select("div.manga-detailmiddle")) {
                genre = select("p:has(span:contains(Genre)) a").joinToString { it.text() }
                description = select("p.mobile-none").text()
            }
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + "${manga.url}?waring=1", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun chapterListSelector() = "ul.detail-chlist li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.select("span").firstOrNull()?.text() ?: it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("span.time").text().toDate()
        }
    }

    private fun String.toDate(): Long {
        return try {
            if (this.contains("ago")) {
                val split = this.split(" ")
                val cal = Calendar.getInstance()
                when {
                    split[1].contains("minute") -> cal.apply { add(Calendar.MINUTE, split[0].toInt()) }.timeInMillis
                    split[1].contains("hour") -> cal.apply { add(Calendar.HOUR, split[0].toInt()) }.timeInMillis
                    else -> 0
                }
            } else {
                SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(this)?.time ?: 0L
            }
        } catch (_: ParseException) {
            0
        }
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val id: String = chapter.url
            .substring(chapter.url.lastIndexOf("/", chapter.url.length - 2))
            .trim('/')

        val pageListHeaders = headersBuilder().add("Referer", "https://www.technologpython.com/")

        return GET("$PAGES_URL/go/jump/?type=nineanime&cid=$id", pageListHeaders.build())
    }

    override fun pageListParse(document: Document): List<Page> {
        val pageListHeaders = headersBuilder().add("Referer", "$baseUrl/manga/").build()

        val scripturl = document.select("script").firstOrNull()?.data()

        val link = scripturl?.split("\"")?.get(1)
        val pages = client.newCall(GET(PAGES_URL + link, pageListHeaders)).execute().asJsoup()

        val script = pages.select("script:containsData(all_imgs_url)").firstOrNull()?.data()
            ?: throw Exception("all_imgsurl not found")
        return Regex(""""(http.*)",""").findAll(script).mapIndexed { i, mr ->
            Page(i, "", mr.groupValues[1])
        }.toList()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("Note: ignored if using text search!"),
        Filter.Separator("-----------------"),
        GenreFilter(),
    )

    private class GenreFilter : UriPartFilter(
        "Genres",
        arrayOf(
            Pair("All", "All"),
            Pair("4-Koma", "4-Koma"),
            Pair("Action", "Action"),
            Pair("Adaptation", "Adaptation"),
            Pair("Adult", "Adult"),
            Pair("Adventure", "Adventure"),
            Pair("Aliens", "Aliens"),
            Pair("All", "category"),
            Pair("Animals", "Animals"),
            Pair("Anthology", "Anthology"),
            Pair("Award Winning", "Award+Winning"),
            Pair("Comedy", "Comedy"),
            Pair("Cooking", "Cooking"),
            Pair("Crime", "Crime"),
            Pair("Crossdressing", "Crossdressing"),
            Pair("Delinquents", "Delinquents"),
            Pair("Demons", "Demons"),
            Pair("Doujinshi", "Doujinshi"),
            Pair("Drama", "Drama"),
            Pair("Ecchi", "Ecchi"),
            Pair("Fantasy", "Fantasy"),
            Pair("Food", "Food"),
            Pair("Full Color", "Full+color"),
            Pair("Game", "Game"),
            Pair("Gender Bender", "Gender+Bender"),
            Pair("Genderswap", "Genderswap"),
            Pair("Ghosts", "Ghosts"),
            Pair("Gossip", "Gossip"),
            Pair("Gyaru", "Gyaru"),
            Pair("Harem", "Harem"),
            Pair("Hentai", "Hentai"),
            Pair("Historical", "Historical"),
            Pair("Horror", "Horror"),
            Pair("Incest", "Incest"),
            Pair("Isekai", "Isekai"),
            Pair("Josei", "Josei"),
            Pair("Kids", "Kids"),
            Pair("Loli", "Loli"),
            Pair("Long Strip", "Long+strip"),
            Pair("Mafia", "Mafia"),
            Pair("Magic", "Magic"),
            Pair("Magical Girls", "Magical+Girls"),
            Pair("Manga", "Manga"),
            Pair("Manhua", "Manhua"),
            Pair("Manhwa", "Manhwa"),
            Pair("Martial Arts", "Martial+Arts"),
            Pair("Mature", "Mature"),
            Pair("Mecha", "Mecha"),
            Pair("Medical", "Medical"),
            Pair("Military", "Military"),
            Pair("Monster Girls", "Monster+girls"),
            Pair("Monsters", "Monsters"),
            Pair("Music", "Music"),
            Pair("Mystery", "Mystery"),
            Pair("N/A", "N%2Fa"),
            Pair("Ninja", "Ninja"),
            Pair("None", "None"),
            Pair("Office Workers", "Office+workers"),
            Pair("Official Colored", "Official+colored"),
            Pair("One Shot", "One+Shot"),
            Pair("Oneshot", "Oneshot"),
            Pair("Parody", "Parody"),
            Pair("Philosophical", "Philosophical"),
            Pair("Police", "Police"),
            Pair("Post Apocalyptic", "Post+apocalyptic"),
            Pair("Psychological", "Psychological"),
            Pair("Reincarnation", "Reincarnation"),
            Pair("Reverse Harem", "Reverse+harem"),
            Pair("Romance", "Romance"),
            Pair("Samurai", "Samurai"),
            Pair("School Life", "School+Life"),
            Pair("Sci Fi", "sci+fi"),
            Pair("Sci-Fi", "Sci-fi"),
            Pair("Seinen", "Seinen"),
            Pair("Shota", "Shota"),
            Pair("Shotacon", "Shotacon"),
            Pair("Shoujo", "Shoujo"),
            Pair("Shoujo Ai", "Shoujo+Ai"),
            Pair("Shounen", "Shounen"),
            Pair("Shounen Ai", "Shounen+Ai"),
            Pair("Slice Of Life", "Slice+Of+Life"),
            Pair("Smut", "Smut"),
            Pair("Sports", "Sports"),
            Pair("Super Power", "Super+power"),
            Pair("Superhero", "Superhero"),
            Pair("Supernatural", "Supernatural"),
            Pair("Survival", "Survival"),
            Pair("Thriller", "Thriller"),
            Pair("Time Travel", "Time+travel"),
            Pair("Toomics", "Toomics"),
            Pair("Tragedy", "Tragedy"),
            Pair("Uncategorized", "Uncategorized"),
            Pair("User Created", "User+created"),
            Pair("Vampire", "Vampire"),
            Pair("Vampires", "Vampires"),
            Pair("Video Games", "Video+games"),
            Pair("Virtual Reality", "Virtual+reality"),
            Pair("Web Comic", "Web+comic"),
            Pair("Webtoon", "Webtoon"),
            Pair("Webtoons", "Webtoons"),
            Pair("Wuxia", "Wuxia"),
            Pair("Yaoi", "Yaoi"),
            Pair("Yuri", "Yuri"),
            Pair("Zombies", "Zombies"),
            Pair("[No Chapters]", "%5Bno+chapters%5D"),
        ),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
