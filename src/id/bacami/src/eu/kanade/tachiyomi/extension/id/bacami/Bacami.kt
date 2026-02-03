package eu.kanade.tachiyomi.extension.id.bacami

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Bacami : ParsedHttpSource() {

    override val name = "Bacami"
    override val baseUrl = "https://bacami.net"
    override val lang = "id"
    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale.ENGLISH)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/custom-search/orderby/score/page/$page/", headers)
    }

    override fun popularMangaSelector() = "article.genre-card"

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "div.paginate a.next.page-numbers"

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/custom-search/orderby/latest/page/$page/", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return GET("$baseUrl/search/$query/page/$page/", headers)
        }

        filters.firstInstanceOrNull<NewKomikFilter>()?.let {
            if (it.state) {
                return GET("$baseUrl/komik-baru/", headers)
            }
        }

        val orderby = filters.firstInstanceOrNull<OrderByFilter>()?.toUriPart() ?: "latest"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "all"
        val type = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "all"
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "all"

        val url = buildString {
            append("$baseUrl/custom-search/")
            if (orderby != "latest") append("orderby/$orderby/")
            if (status != "all") append("status/$status/")
            if (type != "all") append("type/$type/")
            if (genre != "all") append("genre/$genre/")
            append("page/$page/")
        }

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("div.genre-info > a")!!.text()
        setUrlWithoutDomain(element.selectFirst("div.genre-cover > a")!!.attr("href"))
        // Selector diperbaiki untuk menargetkan gambar cover secara spesifik
        thumbnail_url = element.selectFirst("div.genre-cover > a > img")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // =========================== Manga Details ============================
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val content = document.selectFirst("#komik > section.manga-content")!!
        title = content.selectFirst("header > h1")!!.text()
        thumbnail_url = content.selectFirst("figure .image-wrap img")?.let {
            it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
        }
        author = content.selectFirst(".info-item:contains(Author) .info-value")?.text()
            ?.ifBlank { content.selectFirst("div > div > div:nth-child(3) > span.info-value")?.text() }
        genre = content.select("nav > span > a").joinToString { it.text() }
        status = parseStatus(document)

        val altTitle = content.select("p.manga-altname").text()
        description = content.select("p.manga-description").text().let {
            if (altTitle.isNotBlank()) {
                "${it.trim()}\n\nAlternative Title: $altTitle".trim()
            } else {
                it
            }
        }
    }

    private fun parseStatus(document: Document): Int {
        return when {
            document.selectFirst(".hot-tag, .project-tag") != null -> SManga.ONGOING
            document.selectFirst(".tamat-tag") != null -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListSelector() = "ol.chapter-list > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.selectFirst("a.ch-link")!!
        name = link.text().substringAfter("–").trim()
        setUrlWithoutDomain(link.attr("href"))
        date_upload = parseChapterDate(element.select("span.ch-date").text())
    }

    private fun parseChapterDate(date: String): Long = dateFormat.tryParse(date)

    // =============================== Pages ================================
    override fun pageListParse(document: Document): List<Page> {
        val scriptContent = document.selectFirst("script:containsData(imageUrls)")?.data()
            ?: return emptyList()

        val jsonString = scriptContent.substringAfter("imageUrls:").substringBefore("],").plus("]")
        val imageUrls = jsonString.parseAs<List<String>>()
        return imageUrls.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used.")

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filter diabaikan jika menggunakan pencarian teks."),
        Filter.Separator(),
        OrderByFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
        Filter.Separator(),
        Filter.Header("Centang 'Komik Baru' akan mengabaikan filter lain."),
        NewKomikFilter(),
    )

    private class NewKomikFilter : Filter.CheckBox("Komik Baru")

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Latest Updates", "latest"),
            Pair("Alphabetical", "name"),
            Pair("Score", "score"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", "all"),
            Pair("Hot", "hot"),
            Pair("Project", "project"),
            Pair("Completed", "tamat"),
        ),
    )

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("All", "all"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
        ),
    )

    private class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", "all"),
            Pair("Action", "action-2"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Apocalypse", "apocalypse"),
            Pair("Comedy", "comedy"),
            Pair("Comedy Mystery Romance Slice Of Life Supernatural", "comedy-mystery-romance-slice-of-life-supernatural"),
            Pair("Comedy Romance Slice Of Life", "comedy-romance-slice-of-life"),
            Pair("Cooking", "cooking"),
            Pair("Crime", "crime"),
            Pair("Cultivation", "cultivation"),
            Pair("Demons", "demons"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Furry", "furry"),
            Pair("Game", "game"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Genius", "genius"),
            Pair("Gore", "gore"),
            Pair("Harem", "harem"),
            Pair("Hentai", "hentai"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Lolicon", "lolicon"),
            Pair("Long Strip", "long-strip"),
            Pair("Love Polygon", "love-polygon"),
            Pair("Magic", "magic"),
            Pair("Magical Girl", "magical-girl"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Art", "martial-art"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Medical", "medical"),
            Pair("Military", "military"),
            Pair("Monster", "monster"),
            Pair("Monster Girls", "monster-girls"),
            Pair("Monsters", "monsters"),
            Pair("Music", "music"),
            Pair("Mystery", "mystery"),
            Pair("Mystery Shounen", "mystery-shounen"),
            Pair("Mythology", "mythology"),
            Pair("One Shot", "one-shot"),
            Pair("Oneshot", "oneshot"),
            Pair("Parody", "parody"),
            Pair("Philosophical", "philosophical"),
            Pair("Police", "police"),
            Pair("Post-Apocalyptic", "post-apocalyptic"),
            Pair("Psychological", "psychological"),
            Pair("Rebirth", "rebirth"),
            Pair("Reincarnation", "reincarnation"),
            Pair("Romance", "romance"),
            Pair("Romantic Subtext", "romantic-subtext"),
            Pair("Samurai", "samurai"),
            Pair("School", "school"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shotacon", "shotacon"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Space", "space"),
            Pair("Sports", "sports"),
            Pair("Superhero", "superhero"),
            Pair("Supernatural", "supernatural"),
            Pair("Super Power", "super-power"),
            Pair("Survival", "survival"),
            Pair("Suspense", "suspense"),
            Pair("System", "system"),
            Pair("Team Sports", "team-sports"),
            Pair("Thriller", "thriller"),
            Pair("Time Travel", "time-travel"),
            Pair("Tragedy", "tragedy"),
            Pair("Urban", "urban"),
            Pair("Urban Fantasy", "urban-fantasy"),
            Pair("Vampire", "vampire"),
            Pair("Video Game", "video-game"),
            Pair("Villainess", "villainess"),
            Pair("Visual Arts", "visual-arts"),
            Pair("Webtoon", "webtoon"),
            Pair("Webtoons", "webtoons"),
            Pair("Wuxia", "wuxia"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
            Pair("Zombies", "zombies"),
        ),
    )
}
