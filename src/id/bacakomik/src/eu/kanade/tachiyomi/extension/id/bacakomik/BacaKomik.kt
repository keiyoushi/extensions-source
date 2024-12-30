package eu.kanade.tachiyomi.extension.id.bacakomik

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
import java.util.Calendar
import java.util.Locale

class BacaKomik : ParsedHttpSource() {
    override val name = "BacaKomik"
    override val baseUrl = "https://bacakomik.one"
    override val lang = "id"
    override val supportsLatest = true
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // similar/modified theme of "https://komikindo.id"

    // Formerly "Bacakomik" -> now "BacaKomik"
    override val id = 4383360263234319058

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(12, 3)
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=update", headers)
    }

    override fun popularMangaSelector() = "div.animepost"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("div.animposx > a").first()!!.attr("href"))
        manga.title = element.select(".animposx .tt h4").text()
        manga.thumbnail_url = element.select("div.limit img").imgAttr()

        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="
        val url = builtUrl.toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is SortByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.infoanime").first()!!
        val descElement = document.select("div.desc > .entry-content.entry-content-single").first()!!
        val manga = SManga.create()
        manga.title = document.select("#breadcrumbs li:last-child span").text()
        manga.author = document.select(".infox .spe span:contains(Author) :not(b)").text()
        manga.artist = document.select(".infox .spe span:contains(Artis) :not(b)").text()
        val genres = mutableListOf<String>()
        infoElement.select(".infox > .genre-info > a, .infox .spe span:contains(Jenis Komik) a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(document.select(".infox .spe span:contains(Status)").text())
        manga.description = descElement.select("p").text().substringAfter("bercerita tentang ")
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").imgAttr()
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("berjalan") -> SManga.ONGOING
        element.lowercase().contains("tamat") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapter_list li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx a").first()!!
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select(".dt a").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if (date.contains("yang lalu")) {
            val value = date.split(' ')[0].toInt()
            when {
                "detik" in date -> Calendar.getInstance().apply {
                    add(Calendar.SECOND, -value)
                }.timeInMillis
                "menit" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, -value)
                }.timeInMillis
                "jam" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, -value)
                }.timeInMillis
                "hari" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, -value)
                }.timeInMillis
                "minggu" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, -value * 7)
                }.timeInMillis
                "bulan" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, -value)
                }.timeInMillis
                "tahun" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, -value)
                }.timeInMillis
                else -> {
                    0L
                }
            }
        } else {
            try {
                dateFormat.parse(date)?.time ?: 0
            } catch (_: Exception) {
                0L
            }
        }
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
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div:has(>img[alt*=\"Chapter\"]) img").filter { element ->
            val parent = element.parent()
            parent != null && parent.tagName() != "noscript"
        }.forEach { element ->
            val url = element.attr("onError").substringAfter("src='").substringBefore("';")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class TypeFilter : UriPartFilter(
        "Type",
        arrayOf(
            Pair("Default", ""),
            Pair("Manga", "Manga"),
            Pair("Manhwa", "Manhwa"),
            Pair("Manhua", "Manhua"),
            Pair("Comic", "Comic"),
        ),
    )

    private class SortByFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Default", ""),
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
        ),
    )

    private class Genre(name: String, val id: String = name) : Filter.TriState(name)
    private class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("4-Koma", "4-koma"),
        Genre("4-Koma. Comedy", "4-koma-comedy"),
        Genre("Action", "action"),
        Genre("Action. Adventure", "action-adventure"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Echi", "echi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One Shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Samurai", "samurai"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Yuri", "yuri"),
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
