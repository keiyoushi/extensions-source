package eu.kanade.tachiyomi.extension.en.vyvymanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VyvyManga : ParsedHttpSource() {
    override val name = "VyvyManga"

    override val baseUrl = "https://vyvymanga.net"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMM dd, yyy", Locale.US)

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/search" + if (page != 1) "?page=$page" else "", headers)

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String =
        searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is SearchType -> url.addQueryParameter("search_po", filter.selected)
                is SearchDescription -> if (filter.state) url.addQueryParameter("check_search_desc", "1")
                is AuthorSearchType -> url.addQueryParameter("author_po", filter.selected)
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is StatusFilter -> url.addQueryParameter("completed", filter.selected)
                is SortFilter -> url.addQueryParameter("sort", filter.selected)
                is SortType -> url.addQueryParameter("sort_type", filter.selected)
                is GenreFilter -> {
                    filter.state.forEach {
                        if (!it.isIgnored()) url.addQueryParameter(if (it.isIncluded()) "genre[]" else "exclude_genre[]", it.id)
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = ".comic-item"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst(".comic-title")!!.text()
        thumbnail_url = element.selectFirst(".comic-image")!!.absUrl("data-background-image")
    }

    override fun searchMangaNextPageSelector(): String = "[rel=next]"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/search?sort=updated_at" + if (page != 1) "&page=$page" else "", headers)

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? =
        searchMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        artist = document.selectFirst(".pre-title:contains(Artist) ~ a")?.text()
        author = document.selectFirst(".pre-title:contains(Author) ~ a")?.text()
        description = document.selectFirst(".summary > .content")!!.text()
        genre = document.select(".pre-title:contains(Genres) ~ a").joinToString { it.text() }
        status = when (document.selectFirst(".pre-title:contains(Status) ~ span:not(.space)")?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".img-manga")!!.absUrl("src")
    }

    // Chapters
    override fun chapterListSelector(): String =
        ".list-group > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.absUrl("href")
        name = element.selectFirst("span")!!.text()
        date_upload = parseChapterDate(element.selectFirst("> p")?.text())
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request =
        GET(chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.d-block").mapIndexed { index, element ->
            Page(index, "", element.absUrl("data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used.")
    }

    // Other
    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            "ago".endsWith(date) -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("day") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("hour") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("minute") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("second") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    override fun getFilterList(): FilterList {
        return FilterList(
            SearchType(),
            SearchDescription(),
            AuthorSearchType(),
            AuthorFilter(),
            StatusFilter(),
            SortFilter(),
            SortType(),
            GenreFilter(genrePairs),
        )
    }

    abstract class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) : Filter.Select<String>(
        displayName,
        options.map { it.first }.toTypedArray(),
    ) {
        open val selected get() = options[state].second.takeUnless { it.isEmpty() }
    }
    class SearchType : SelectFilter(
        "Title should contain/begin/end with typed text",
        arrayOf(
            Pair("Contain", "0"),
            Pair("Begin", "1"),
            Pair("End", "2"),
        ),
    )
    class SearchDescription : Filter.CheckBox("Search In Description")
    class AuthorSearchType : SelectFilter(
        "Author should contain/begin/end with typed text",
        arrayOf(
            Pair("Contain", "0"),
            Pair("Begin", "1"),
            Pair("End", "2"),
        ),
    )
    class AuthorFilter : Filter.Text("Author")
    class StatusFilter : SelectFilter(
        "Status",
        arrayOf(
            Pair("", "2"),
            Pair("Ongoing", "0"),
            Pair("Completed", "1"),
        ),
    )
    class SortFilter : SelectFilter(
        "Sort by",
        arrayOf(
            Pair("Viewed", "viewed"),
            Pair("Scored", "scored"),
            Pair("Newest", "created_at"),
            Pair("Latest Update", "updated_at"),
        ),
    )
    class SortType : SelectFilter(
        "Sort type",
        arrayOf(
            Pair("Descending", "desc"),
            Pair("Ascending", "asc"),
        ),
    )
    class Genre(name: String, val id: String) : Filter.TriState(name)

    class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    private val genrePairs: List<Genre> = genres.map { genre ->
        val nameUrl = "${genre.first.replace(" ", "+")}-${genre.second}-${genre.first.lowercase().replace(" ", "_")}"
        Genre(genre.first, nameUrl)
    }
    private val genres: List<Pair<String, String>>
        get() = listOf(
            Pair("4-koma", "89"),
            Pair("Action", "1"),
            Pair("Adaptation", "72"),
            Pair("Adventure", "2"),
            Pair("Age Gap", "165"),
            Pair("All Ages", "122"),
            Pair("Animals", "90"),
            Pair("Anime", "152"),
            Pair("Anthology", "101"),
            Pair("Award winning", "91"),
            Pair("Bara", "116"),
            Pair("Bara(ML)", "173"),
            Pair("Beasts", "175"),
            Pair("Bengali", "138"),
            Pair("Bloody", "183"),
            Pair("Boys", "185"),
            Pair("Cars", "49"),
            Pair("Cheating/Infidelity", "176"),
            Pair("Childhood Friends", "164"),
            Pair("College life", "157"),
            Pair("Comedy", "15"),
            Pair("Comic", "130"),
            Pair("Cooking", "63"),
            Pair("Crime", "81"),
            Pair("Crossdressing", "105"),
            Pair("Delinquents", "73"),
            Pair("Dementia", "48"),
            Pair("Demons", "3"),
            Pair("Doujinshi", "55"),
            Pair("Drama", "4"),
            Pair("Dungeons", "171"),
            Pair("Ecchi", "27"),
            Pair("Erotica", "146"),
            Pair("Fantasy", "7"),
            Pair("Fetish", "180"),
            Pair("Full color", "82"),
            Pair("Full Colored", "78"),
            Pair("Game", "33"),
            Pair("Gender Bender", "39"),
            Pair("Genderswap", "159"),
            Pair("Ghosts", "97"),
            Pair("Girls", "184"),
            Pair("Gossip", "123"),
            Pair("Gyaru", "104"),
            Pair("Harem", "38"),
            Pair("Harlequin", "178"),
            Pair("Historical", "12"),
            Pair("Horror", "5"),
            Pair("Incest", "98"),
            Pair("Indonesian", "137"),
            Pair("Isekai", "69"),
            Pair("Italian", "136"),
            Pair("Japanese", "129"),
            Pair("Josei", "35"),
            Pair("Josei(W)", "166"),
            Pair("Kids", "42"),
            Pair("Long strip", "76"),
            Pair("Mafia", "83"),
            Pair("Magic", "34"),
            Pair("Magical girls", "88"),
            Pair("Manga", "127"),
            Pair("Manhua", "62"),
            Pair("Manhwa", "61"),
            Pair("Martial Arts", "37"),
            Pair("Mature", "60"),
            Pair("Mecha", "36"),
            Pair("Medical", "66"),
            Pair("Military", "8"),
            Pair("Monster girls", "95"),
            Pair("Monsters", "84"),
            Pair("Music", "32"),
            Pair("Mystery", "11"),
            Pair("Netorare/NTR", "158"),
            Pair("Ninja", "93"),
            Pair("Non-human", "186"),
            Pair("NOVEL", "56"),
            Pair("OEL", "131"),
            Pair("Office", "126"),
            Pair("Omegaverse", "154"),
            Pair("One Shot", "67"),
            Pair("Parody", "30"),
            Pair("Philosophical", "100"),
            Pair("Police", "46"),
            Pair("Pornographic", "147"),
            Pair("Post apocalyptic", "94"),
            Pair("Post-Apocalyptic", "140"),
            Pair("Psychological", "9"),
            Pair("Regression", "170"),
            Pair("Reincarnation", "74"),
            Pair("Revenge", "182"),
            Pair("Reverse harem", "79"),
            Pair("Romance", "25"),
            Pair("Royal family", "155"),
            Pair("Royalty", "174"),
            Pair("Russian", "139"),
            Pair("Samurai", "18"),
            Pair("School life", "59"),
            Pair("Sci-fi", "148"),
            Pair("Seinen", "10"),
            Pair("Seinen(M)", "167"),
            Pair("Sexual violence", "117"),
            Pair("Shotacon", "160"),
            Pair("Shoujo", "28"),
            Pair("Shoujo Ai", "40"),
            Pair("Shounen", "13"),
            Pair("Shounen Ai", "44"),
            Pair("Shounen(B)", "168"),
            Pair("Showbiz", "177"),
            Pair("Si-fi", "142"),
            Pair("Silver & Golden", "187"),
            Pair("Slice of Life", "19"),
            Pair("SM/BDSM/SUB-DOM", "181"),
            Pair("Smut", "65"),
            Pair("Space", "29"),
            Pair("Sports", "22"),
            Pair("Super Power", "17"),
            Pair("Superhero", "109"),
            Pair("Supernatural", "6"),
            Pair("Survival", "85"),
            Pair("SWORDS", "149"),
            Pair("Thriller", "31"),
            Pair("Time travel", "80"),
            Pair("Toomics", "120"),
            Pair("Traditional games", "113"),
            Pair("Tragedy", "68"),
            Pair("Transmigration", "179"),
            Pair("Uncategorized", "50"),
            Pair("Uncensored", "124"),
            Pair("User created", "102"),
            Pair("Vampire", "151"),
            Pair("Vampires", "103"),
            Pair("Vanilla", "125"),
            Pair("Video games", "75"),
            Pair("Villainess", "119"),
            Pair("Violence", "169"),
            Pair("Virtual reality", "110"),
            Pair("Webtoons", "141"),
            Pair("Western", "172"),
            Pair("Wuxia", "106"),
            Pair("Yakuzas", "156"),
            Pair("Yaoi", "51"),
            Pair("Yuri", "54"),
            Pair("Zh-hk", "135"),
            Pair("Zombies", "108"),

            )
}
