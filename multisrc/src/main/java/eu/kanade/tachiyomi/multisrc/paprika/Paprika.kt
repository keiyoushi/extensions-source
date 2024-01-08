package eu.kanade.tachiyomi.multisrc.paprika

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

abstract class Paprika(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/popular-manga?page=$page")
    }

    override fun popularMangaSelector() = "div.media"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a:has(h4)").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/latest-manga?page=$page")
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search?q=$query&page=$page")
        } else {
            val url = "$baseUrl/mangas/".toHttpUrlOrNull()!!.newBuilder()
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> url.addPathSegment(filter.toUriPart())
                    is OrderFilter -> url.addQueryParameter("orderby", filter.toUriPart())
                    else -> {}
                }
            }
            url.addQueryParameter("page", page.toString())
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("div.manga-detail h1").text()
            thumbnail_url = document.select("div.manga-detail img").attr("abs:src")
            document.select("div.media-body p").html().split("<br>").forEach {
                with(Jsoup.parse(it).text()) {
                    when {
                        this.startsWith("Author") -> author = this.substringAfter(":").trim()
                        this.startsWith("Artist") -> artist = this.substringAfter(":").trim()
                        this.startsWith("Genre") -> genre = this.substringAfter(":").trim().replace(";", ",")
                        this.startsWith("Status") -> status = this.substringAfter(":").trim().toStatus()
                    }
                }
            }
            description = document.select("div.manga-content p").joinToString("\n") { it.text() }
        }
    }

    fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        this.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    /**
     * This theme has 3 chapter blocks: latest chapters with dates, all chapters without dates, and upcoming chapters
     * Avoid parsing the upcoming chapters and filter out duplicate chapters
     */

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.select("div.manga-detail h1").text()
        return document.select(chapterListSelector()).map { chapterFromElement(it, mangaTitle) }.distinctBy { it.url }
    }

    override fun chapterListSelector() = "div.total-chapter:has(h2) li"

    // never called
    override fun chapterFromElement(element: Element): SChapter {
        throw Exception("unreachable code was reached!")
    }

    open fun chapterFromElement(element: Element, mangaTitle: String): SChapter {
        return SChapter.create().apply {
            element.select("a").let {
                name = it.text().substringAfter("$mangaTitle ")
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("div.small").firstOrNull()?.text().toDate()
        }
    }

    private val currentYear by lazy { Calendar.getInstance(Locale.US)[1].toString().takeLast(2) }

    fun String?.toDate(): Long {
        this ?: return 0L
        return try {
            when {
                this.contains("yesterday", ignoreCase = true) -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
                this.contains("ago", ignoreCase = true) -> {
                    val trimmedDate = this.substringBefore(" ago").removeSuffix("s").split(" ")
                    val num = trimmedDate[0].toIntOrNull() ?: 1 // for "an hour ago"
                    val calendar = Calendar.getInstance()
                    when (trimmedDate[1]) {
                        "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -num) }
                        "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -num) }
                        "minute" -> calendar.apply { add(Calendar.MINUTE, -num) }
                        "second" -> calendar.apply { add(Calendar.SECOND, -num) }
                        else -> null
                    }?.timeInMillis ?: 0L
                }
                else ->
                    SimpleDateFormat("MMM d yy", Locale.US)
                        .parse("${this.substringBefore(",")} $currentYear")?.time ?: 0
            }
        } catch (_: Exception) {
            0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#arraydata").text().split(",").mapIndexed { i, url ->
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Filters

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        OrderFilter(getOrderList()),
        GenreFilter(getGenreList()),
    )

    class OrderFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Category", vals)

    private fun getOrderList() = arrayOf(
        Pair("Views", "2"),
        Pair("Latest", "3"),
        Pair("A-Z", "1"),
    )

    class GenreFilter(vals: Array<Pair<String, String>>) : UriPartFilter("Category", vals)

    private fun getGenreList() = arrayOf(
        Pair("4 koma", "4-koma"),
        Pair("Action", "action"),
        Pair("Adaptation", "adaptation"),
        Pair("Adult", "adult"),
        Pair("Adventure", "adventure"),
        Pair("Aliens", "aliens"),
        Pair("Animals", "animals"),
        Pair("Anthology", "anthology"),
        Pair("Award winning", "award-winning"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Crime", "crime"),
        Pair("Crossdressing", "crossdressing"),
        Pair("Delinquents", "delinquents"),
        Pair("Demons", "demons"),
        Pair("Doujinshi", "doujinshi"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fan colored", "fan-colored"),
        Pair("Fantasy", "fantasy"),
        Pair("Food", "food"),
        Pair("Full color", "full-color"),
        Pair("Game", "game"),
        Pair("Gender bender", "gender-bender"),
        Pair("Genderswap", "genderswap"),
        Pair("Ghosts", "ghosts"),
        Pair("Gore", "gore"),
        Pair("Gossip", "gossip"),
        Pair("Gyaru", "gyaru"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Kids", "kids"),
        Pair("Loli", "loli"),
        Pair("Lolicon", "lolicon"),
        Pair("Long strip", "long-strip"),
        Pair("Mafia", "mafia"),
        Pair("Magic", "magic"),
        Pair("Magical girls", "magical-girls"),
        Pair("Manhwa", "manhwa"),
        Pair("Martial arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Military", "military"),
        Pair("Monster girls", "monster-girls"),
        Pair("Monsters", "monsters"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Ninja", "ninja"),
        Pair("Office workers", "office-workers"),
        Pair("Official colored", "official-colored"),
        Pair("One shot", "one-shot"),
        Pair("Parody", "parody"),
        Pair("Philosophical", "philosophical"),
        Pair("Police", "police"),
        Pair("Post apocalyptic", "post-apocalyptic"),
        Pair("Psychological", "psychological"),
        Pair("Reincarnation", "reincarnation"),
        Pair("Reverse harem", "reverse-harem"),
        Pair("Romance", "romance"),
        Pair("Samurai", "samurai"),
        Pair("School life", "school-life"),
        Pair("Sci fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shota", "shota"),
        Pair("Shotacon", "shotacon"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Shounen ai", "shounen-ai"),
        Pair("Slice of life", "slice-of-life"),
        Pair("Smut", "smut"),
        Pair("Space", "space"),
        Pair("Sports", "sports"),
        Pair("Super power", "super-power"),
        Pair("Superhero", "superhero"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Suspense", "suspense"),
        Pair("Thriller", "thriller"),
        Pair("Time travel", "time-travel"),
        Pair("Toomics", "toomics"),
        Pair("Traditional games", "traditional-games"),
        Pair("Tragedy", "tragedy"),
        Pair("User created", "user-created"),
        Pair("Vampire", "vampire"),
        Pair("Vampires", "vampires"),
        Pair("Video games", "video-games"),
        Pair("Virtual reality", "virtual-reality"),
        Pair("Web comic", "web-comic"),
        Pair("Webtoon", "webtoon"),
        Pair("Wuxia", "wuxia"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
        Pair("Zombies", "zombies"),
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
