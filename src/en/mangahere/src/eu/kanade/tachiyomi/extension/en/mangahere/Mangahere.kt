package eu.kanade.tachiyomi.extension.en.mangahere

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mangahere : ParsedHttpSource() {

    override val id: Long = 2

    override val name = "Mangahere"

    override val baseUrl = "https://www.mangahere.cc"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)

    override val client: OkHttpClient = super.client.newBuilder()
        .cookieJar(
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}
                override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                    return ArrayList<Cookie>().apply {
                        add(
                            Cookie.Builder()
                                .domain("www.mangahere.cc")
                                .path("/")
                                .name("isAdult")
                                .value("1")
                                .build(),
                        )
                    }
                }
            },
        )
        .build()

    override fun popularMangaSelector() = ".manga-list-1-list li"

    override fun latestUpdatesSelector() = ".manga-list-1-list li"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/directory/$page.htm", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/directory/$page.htm?latest", headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val titleElement = element.select("a").first()!!
        manga.title = titleElement.attr("title")
        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.thumbnail_url = element.select("img.manga-list-1-cover")
            .first()?.attr("src")

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun popularMangaNextPageSelector() = "div.pager-list-left a:last-child"

    override fun latestUpdatesNextPageSelector() = "div.pager-list-left a:last-child"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrlOrNull()!!.newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is TypeList -> url.addEncodedQueryParameter("type", types[filter.values[filter.state]].toString())
                is CompletionList -> url.addEncodedQueryParameter("st", filter.state.toString())
                is RatingList -> {
                    url.addEncodedQueryParameter("rating_method", "gt")
                    url.addEncodedQueryParameter("rating", filter.state.toString())
                }
                is GenreList -> {
                    val includeGenres = mutableSetOf<Int>()
                    val excludeGenres = mutableSetOf<Int>()
                    filter.state.forEach { genre ->
                        if (genre.isIncluded()) includeGenres.add(genre.id)
                        if (genre.isExcluded()) excludeGenres.add(genre.id)
                    }
                    url.apply {
                        addEncodedQueryParameter("genres", includeGenres.joinToString(","))
                        addEncodedQueryParameter("nogenres", excludeGenres.joinToString(","))
                    }
                }
                is ArtistFilter -> {
                    url.addEncodedQueryParameter("artist_method", "cw")
                    url.addEncodedQueryParameter("artist", filter.state)
                }
                is AuthorFilter -> {
                    url.addEncodedQueryParameter("author_method", "cw")
                    url.addEncodedQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addEncodedQueryParameter("released_method", "eq")
                    url.addEncodedQueryParameter("released", filter.state)
                }
                else -> {}
            }
        }

        url.apply {
            addEncodedQueryParameter("page", page.toString())
            addEncodedQueryParameter("title", query)
            addEncodedQueryParameter("sort", null)
            addEncodedQueryParameter("stype", 1.toString())
            addEncodedQueryParameter("name", null)
        }

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = ".manga-list-4-list > li"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleEl = element.select(".manga-list-4-item-title > a").first()
        manga.setUrlWithoutDomain(titleEl?.attr("href") ?: "")
        manga.title = titleEl?.attr("title") ?: ""
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.pager-list-left a:last-child"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = document.select(".detail-info-right-say > a").first()?.text()
        manga.genre = document.select(".detail-info-right-tag-list > a").joinToString { it.text() }
        manga.description = document.select(".fullcontent").first()?.text()
        manga.thumbnail_url = document.select("img.detail-info-cover-img").first()
            ?.attr("src")

        document.select("span.detail-info-right-title-tip").first()?.text()?.also { statusText ->
            when {
                statusText.contains("ongoing", true) -> manga.status = SManga.ONGOING
                statusText.contains("completed", true) -> manga.status = SManga.COMPLETED
                else -> manga.status = SManga.UNKNOWN
            }
        }

        // Get a chapter, check if the manga is licensed.
        val aChapterURL = chapterFromElement(document.select(chapterListSelector()).first()!!).url
        val aChapterDocument = client.newCall(GET("$baseUrl$aChapterURL", headers)).execute().asJsoup()
        if (aChapterDocument.select("p.detail-block-content").hasText()) manga.status = SManga.LICENSED

        return manga
    }

    override fun chapterListSelector() = "ul.detail-main-list > li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.select("a").first()!!.attr("href"))
        chapter.name = element.select("a p.title3").first()!!.text()
        chapter.date_upload = element.select("a p.title2").first()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return if ("Today" in date || " ago" in date) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else if ("Yesterday" in date) {
            Calendar.getInstance().apply {
                add(Calendar.DATE, -1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else {
            try {
                SimpleDateFormat("MMM dd,yyyy", Locale.ENGLISH).parse(date)?.time ?: 0L
            } catch (e: ParseException) {
                0L
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val bar = document.select("script[src*=chapter_bar]")
        val quickJs = QuickJs.create()

        /*
            function to drop last imageUrl if it's broken/unneccesary, working imageUrls are incremental (e.g. t001, t002, etc); if the difference between
            the last two isn't 1 or doesn't have an Int at the end of the last imageUrl's filename, drop last Page
         */
        fun List<Page>.dropLastIfBroken(): List<Page> {
            val list = this.takeLast(2).map { page ->
                try {
                    page.imageUrl!!.substringBeforeLast(".").substringAfterLast("/").takeLast(2).toInt()
                } catch (_: NumberFormatException) {
                    return this.dropLast(1)
                }
            }
            return when {
                list[0] == 0 && 100 - list[1] == 1 -> this
                list[1] - list[0] == 1 -> this
                else -> this.dropLast(1)
            }
        }

        // if-branch is for webtoon reader, else is for page-by-page
        return if (bar.isNotEmpty()) {
            val script = document.select("script:containsData(function(p,a,c,k,e,d))").html().removePrefix("eval")
            val deobfuscatedScript = quickJs.evaluate(script).toString()
            val urls = deobfuscatedScript.substringAfter("newImgs=['").substringBefore("'];").split("','")
            quickJs.close()

            urls.mapIndexed { index, s -> Page(index, "", "https:$s") }
        } else {
            val html = document.html()
            val link = document.location()

            var secretKey = extractSecretKey(html, quickJs)

            val chapterIdStartLoc = html.indexOf("chapterid")
            val chapterId = html.substring(
                chapterIdStartLoc + 11,
                html.indexOf(";", chapterIdStartLoc),
            ).trim()

            val chapterPagesElement = document.select(".pager-list-left > span").first()!!
            val pagesLinksElements = chapterPagesElement.select("a")
            val pagesNumber = pagesLinksElements[pagesLinksElements.size - 2].attr("data-page").toInt()

            val pageBase = link.substring(0, link.lastIndexOf("/"))

            IntRange(1, pagesNumber).map { i ->
                val pageLink = "$pageBase/chapterfun.ashx?cid=$chapterId&page=$i&key=$secretKey"

                var responseText = ""

                for (tr in 1..3) {
                    val request = Request.Builder()
                        .url(pageLink)
                        .addHeader("Referer", link)
                        .addHeader("Accept", "*/*")
                        .addHeader("Accept-Language", "en-US,en;q=0.9")
                        .addHeader("Connection", "keep-alive")
                        .addHeader("Host", "www.mangahere.cc")
                        .addHeader("User-Agent", System.getProperty("http.agent") ?: "")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val response = client.newCall(request).execute()
                    responseText = response.body.string()

                    if (responseText.isNotEmpty()) {
                        break
                    } else {
                        secretKey = ""
                    }
                }

                val deobfuscatedScript = quickJs.evaluate(responseText.removePrefix("eval")).toString()

                val baseLinkStartPos = deobfuscatedScript.indexOf("pix=") + 5
                val baseLinkEndPos = deobfuscatedScript.indexOf(";", baseLinkStartPos) - 1
                val baseLink = deobfuscatedScript.substring(baseLinkStartPos, baseLinkEndPos)

                val imageLinkStartPos = deobfuscatedScript.indexOf("pvalue=") + 9
                val imageLinkEndPos = deobfuscatedScript.indexOf("\"", imageLinkStartPos)
                val imageLink = deobfuscatedScript.substring(imageLinkStartPos, imageLinkEndPos)

                Page(i - 1, "", "https:$baseLink$imageLink")
            }
        }
            .dropLastIfBroken()
            .also { quickJs.close() }
    }

    private fun extractSecretKey(html: String, quickJs: QuickJs): String {
        val secretKeyScriptLocation = html.indexOf("eval(function(p,a,c,k,e,d)")
        val secretKeyScriptEndLocation = html.indexOf("</script>", secretKeyScriptLocation)
        val secretKeyScript = html.substring(secretKeyScriptLocation, secretKeyScriptEndLocation).removePrefix("eval")

        val secretKeyDeobfuscatedScript = quickJs.evaluate(secretKeyScript).toString()

        val secretKeyStartLoc = secretKeyDeobfuscatedScript.indexOf("'")
        val secretKeyEndLoc = secretKeyDeobfuscatedScript.indexOf(";")

        val secretKeyResultScript = secretKeyDeobfuscatedScript.substring(
            secretKeyStartLoc,
            secretKeyEndLoc,
        )

        return quickJs.evaluate(secretKeyResultScript).toString()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    private class Genre(title: String, val id: Int) : Filter.TriState(title)

    private class TypeList(types: Array<String>) : Filter.Select<String>("Type", types, 1)
    private class CompletionList(completions: Array<String>) : Filter.Select<String>("Completed series", completions, 0)
    private class RatingList(ratings: Array<String>) : Filter.Select<String>("Minimum rating", ratings, 0)
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

    private class ArtistFilter(name: String) : Filter.Text(name)
    private class AuthorFilter(name: String) : Filter.Text(name)
    private class YearFilter(name: String) : Filter.Text(name)

    override fun getFilterList() = FilterList(
        TypeList(types.keys.toList().sorted().toTypedArray()),
        ArtistFilter("Artist"),
        AuthorFilter("Author"),
        GenreList(genres()),
        RatingList(ratings),
        YearFilter("Year released"),
        CompletionList(completions),
    )

    private val types = hashMapOf(
        "Japanese Manga" to 1,
        "Korean Manhwa" to 2,
        "Chinese Manhua" to 3,
        "European Manga" to 4,
        "American Manga" to 5,
        "Hong Kong Manga" to 6,
        "Other Manga" to 7,
        "Any" to 0,
    )

    private val completions = arrayOf("Either", "No", "Yes")
    private val ratings = arrayOf("No Stars", "1 Star", "2 Stars", "3 Stars", "4 Stars", "5 Stars")

    private fun genres() = arrayListOf(
        Genre("Action", 1),
        Genre("Adventure", 2),
        Genre("Comedy", 3),
        Genre("Fantasy", 4),
        Genre("Historical", 5),
        Genre("Horror", 6),
        Genre("Martial Arts", 7),
        Genre("Mystery", 8),
        Genre("Romance", 9),
        Genre("Shounen Ai", 10),
        Genre("Supernatural", 11),
        Genre("Drama", 12),
        Genre("Shounen", 13),
        Genre("School Life", 14),
        Genre("Shoujo", 15),
        Genre("Gender Bender", 16),
        Genre("Josei", 17),
        Genre("Psychological", 18),
        Genre("Seinen", 19),
        Genre("Slice of Life", 20),
        Genre("Sci-fi", 21),
        Genre("Ecchi", 22),
        Genre("Harem", 23),
        Genre("Shoujo Ai", 24),
        Genre("Yuri", 25),
        Genre("Mature", 26),
        Genre("Tragedy", 27),
        Genre("Yaoi", 28),
        Genre("Doujinshi", 29),
        Genre("Sports", 30),
        Genre("Adult", 31),
        Genre("One Shot", 32),
        Genre("Smut", 33),
        Genre("Mecha", 34),
        Genre("Shotacon", 35),
        Genre("Lolicon", 36),
        Genre("Webtoons", 37),
    )
}
