package eu.kanade.tachiyomi.extension.id.komikindoid

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class KomikIndoID : ParsedHttpSource() {
    override val name = "KomikIndoID"
    override val baseUrl = "https://komikindo.tv"
    override val lang = "id"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    // similar/modified theme of "https://bacakomik.co"
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-manga/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/daftar-manga/page/$page/?order=update", headers)
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
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        manga.title = element.select("div.tt h4").text()
        element.select("div.animposx > a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/daftar-manga/page/$page/".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("title", query)
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is SortFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is OriginalLanguageFilter -> {
                    filter.state.forEach { lang ->
                        if (lang.state) {
                            url.addQueryParameter("type[]", lang.id)
                        }
                    }
                }
                is FormatFilter -> {
                    filter.state.forEach { format ->
                        if (format.state) {
                            url.addQueryParameter("format[]", format.id)
                        }
                    }
                }
                is DemographicFilter -> {
                    filter.state.forEach { demographic ->
                        if (demographic.state) {
                            url.addQueryParameter("demografis[]", demographic.id)
                        }
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            url.addQueryParameter("status[]", status.id)
                        }
                    }
                }
                is ContentRatingFilter -> {
                    filter.state.forEach { rating ->
                        if (rating.state) {
                            url.addQueryParameter("konten[]", rating.id)
                        }
                    }
                }
                is ThemeFilter -> {
                    filter.state.forEach { theme ->
                        if (theme.state) {
                            url.addQueryParameter("tema[]", theme.id)
                        }
                    }
                }
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            url.addQueryParameter("genre[]", genre.id)
                        }
                    }
                }
                else -> {}
            }
        }
        return GET(url.toString(), headers)
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.infoanime").first()!!
        val descElement = document.select("div.desc > .entry-content.entry-content-single").first()!!
        val manga = SManga.create()
        // need authorCleaner to take "pengarang:" string to remove it from author
        val authorCleaner = document.select(".infox .spe b:contains(Pengarang)").text()
        manga.author = document.select(".infox .spe span:contains(Pengarang)").text().substringAfter(authorCleaner)
        val artistCleaner = document.select(".infox .spe b:contains(Ilustrator)").text()
        manga.artist = document.select(".infox .spe span:contains(Ilustrator)").text().substringAfter(artistCleaner)
        val genres = mutableListOf<String>()
        infoElement.select(".infox .genre-info a, .infox .spe span:contains(Grafis:) a, .infox .spe span:contains(Tema:) a, .infox .spe span:contains(Konten:) a, .infox .spe span:contains(Jenis Komik:) a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select(".infox > .spe > span:nth-child(2)").text())
        manga.description = descElement.select("p").text().substringAfter("bercerita tentang ")
        // Add alternative name to manga description
        val altName = document.selectFirst(".infox > .spe > span:nth-child(1)")?.text().takeIf { it.isNullOrBlank().not() }
        altName?.let {
            manga.description = manga.description + "\n\n$altName"
        }
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").attr("src").substringBeforeLast("?")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("berjalan", true) -> SManga.ONGOING
        element.contains("tamat", true) -> SManga.COMPLETED
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
                    add(Calendar.SECOND, value * -1)
                }.timeInMillis
                "menit" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "jam" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "hari" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "minggu" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "bulan" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "tahun" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
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
        document.select("div.img-landmine img").forEach { element ->
            val url = element.attr("onError").substringAfter("src='").substringBefore("';")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList(
        SortFilter(),
        Filter.Header("NOTE: Ignored if using text search!"),
        AuthorFilter(),
        YearFilter(),
        Filter.Separator(),
        OriginalLanguageFilter(getOriginalLanguage()),
        FormatFilter(getFormat()),
        DemographicFilter(getDemographic()),
        StatusFilter(getStatus()),
        ContentRatingFilter(getContentRating()),
        ThemeFilter(getTheme()),
        GenreFilter(getGenre()),
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class SortFilter : UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("A-Z", "title"),
            Pair("Z-A", "titlereverse"),
            Pair("Latest Update", "update"),
            Pair("Latest Added", "latest"),
            Pair("Popular", "popular"),
        ),
    )

    private class OriginalLanguage(name: String, val id: String = name) : Filter.CheckBox(name)
    private class OriginalLanguageFilter(originalLanguage: List<OriginalLanguage>) :
        Filter.Group<OriginalLanguage>("Original language", originalLanguage)
    private fun getOriginalLanguage() = listOf(
        OriginalLanguage("Japanese (Manga)", "Manga"),
        OriginalLanguage("Chinese (Manhua)", "Manhua"),
        OriginalLanguage("Korean (Manhwa)", "Manhwa"),
    )

    private class Format(name: String, val id: String = name) : Filter.CheckBox(name)
    private class FormatFilter(formatList: List<Format>) :
        Filter.Group<Format>("Format", formatList)
    private fun getFormat() = listOf(
        Format("Black & White", "0"),
        Format("Full Color", "1"),
    )

    private class Demographic(name: String, val id: String = name) : Filter.CheckBox(name)
    private class DemographicFilter(demographicList: List<Demographic>) :
        Filter.Group<Demographic>("Publication Demographic", demographicList)
    private fun getDemographic() = listOf(
        Demographic("Josei", "josei"),
        Demographic("Seinen", "seinen"),
        Demographic("Shoujo", "shoujo"),
        Demographic("Shounen", "shounen"),
    )

    private class Status(name: String, val id: String = name) : Filter.CheckBox(name)
    private class StatusFilter(statusList: List<Status>) :
        Filter.Group<Status>("Status", statusList)
    private fun getStatus() = listOf(
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
    )

    private class ContentRating(name: String, val id: String = name) : Filter.CheckBox(name)
    private class ContentRatingFilter(contentRating: List<ContentRating>) :
        Filter.Group<ContentRating>("Content Rating", contentRating)
    private fun getContentRating() = listOf(
        ContentRating("Ecchi", "ecchi"),
        ContentRating("Gore", "gore"),
        ContentRating("Sexual Violence", "sexual-violence"),
        ContentRating("Smut", "smut"),
    )

    private class Theme(name: String, val id: String = name) : Filter.CheckBox(name)
    private class ThemeFilter(themeList: List<Theme>) :
        Filter.Group<Theme>("Story Theme", themeList)
    private fun getTheme() = listOf(
        Theme("Alien", "aliens"),
        Theme("Animal", "animals"),
        Theme("Cooking", "cooking"),
        Theme("Crossdressing", "crossdressing"),
        Theme("Delinquent", "delinquents"),
        Theme("Demon", "demons"),
        Theme("Ecchi", "ecchi"),
        Theme("Gal", "gyaru"),
        Theme("Genderswap", "genderswap"),
        Theme("Ghost", "ghosts"),
        Theme("Harem", "harem"),
        Theme("Incest", "incest"),
        Theme("Loli", "loli"),
        Theme("Mafia", "mafia"),
        Theme("Magic", "magic"),
        Theme("Martial Arts", "martial-arts"),
        Theme("Military", "military"),
        Theme("Monster Girls", "monster-girls"),
        Theme("Monsters", "monsters"),
        Theme("Music", "music"),
        Theme("Ninja", "ninja"),
        Theme("Office Workers", "office-workers"),
        Theme("Police", "police"),
        Theme("Post-Apocalyptic", "post-apocalyptic"),
        Theme("Reincarnation", "reincarnation"),
        Theme("Reverse Harem", "reverse-harem"),
        Theme("Samurai", "samurai"),
        Theme("School Life", "school-life"),
        Theme("Shota", "shota"),
        Theme("Smut", "smut"),
        Theme("Supernatural", "supernatural"),
        Theme("Survival", "survival"),
        Theme("Time Travel", "time-travel"),
        Theme("Traditional Games", "traditional-games"),
        Theme("Vampires", "vampires"),
        Theme("Video Games", "video-games"),
        Theme("Villainess", "villainess"),
        Theme("Virtual Reality", "virtual-reality"),
        Theme("Zombies", "zombies"),
    )

    private class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
    private class GenreFilter(genreList: List<Genre>) :
        Filter.Group<Genre>("Genre", genreList)
    private fun getGenre() = listOf(
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Drama", "drama"),
        Genre("Fantasy", "fantasy"),
        Genre("Girls Love", "girls-love"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Magical Girls", "magical-girls"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Philosophical", "philosophical"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Superhero", "superhero"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri"),
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
