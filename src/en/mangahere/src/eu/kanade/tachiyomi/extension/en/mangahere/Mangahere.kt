package eu.kanade.tachiyomi.extension.en.mangahere

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Mangahere : HttpSource() {

    override val id: Long = 2

    override val name = "Mangahere"

    override val baseUrl = "https://www.mangahere.cc"

    override val lang = "en"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val cookieInterceptor = CookieInterceptor(
        baseUrl.substringAfter("://"),
        listOf(
            "isAdult" to "1",
        ),
    )

    private val notRateLimitClient: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override val client: OkHttpClient = notRateLimitClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .build()

    private val dateFormat = SimpleDateFormat("MMM dd,yyyy", Locale.ENGLISH)

    // Popular Manga

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/directory/$page.htm", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(popularMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaSelector() = ".manga-list-1-list li"

    private fun popularMangaNextPageSelector() = "div.pager-list-left a:last-child"

    private fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleElement = element.selectFirst("a")!!
        manga.title = titleElement.attr("title")
        manga.setUrlWithoutDomain(titleElement.absUrl("href"))
        manga.thumbnail_url = element.selectFirst("img.manga-list-1-cover")?.absUrl("src")
        return manga
    }

    // Latest Updates

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/directory/$page.htm?latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesSelector() = ".manga-list-1-list li"

    private fun latestUpdatesNextPageSelector() = "div.pager-list-left a:last-child"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()

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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector() = ".manga-list-4-list > li"

    private fun searchMangaNextPageSelector() = "div.pager-list-left a:last-child"

    private fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val titleEl = element.selectFirst(".manga-list-4-item-title > a")
        manga.setUrlWithoutDomain(titleEl?.absUrl("href") ?: "")
        manga.title = titleEl?.attr("title") ?: ""
        manga.thumbnail_url = element
            .selectFirst("img.manga-list-4-cover")
            ?.absUrl("src")
        return manga
    }

    // Manga Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        manga.author = document.selectFirst(".detail-info-right-say > a")?.text()
        manga.genre = document.select(".detail-info-right-tag-list > a").joinToString { it.text() }
        manga.description = document.selectFirst(".fullcontent")?.text()
        manga.thumbnail_url = document.selectFirst("img.detail-info-cover-img")?.absUrl("src")

        document.selectFirst("span.detail-info-right-title-tip")?.text()?.also { statusText ->
            when {
                statusText.contains("ongoing", true) -> manga.status = SManga.ONGOING
                statusText.contains("completed", true) -> manga.status = SManga.COMPLETED
                else -> manga.status = SManga.UNKNOWN
            }
        }

        // Get a chapter, check if the manga is licensed.
        val aChapterURL = chapterFromElement(document.selectFirst(chapterListSelector())!!).url
        val aChapterDocument = client.newCall(GET("$baseUrl$aChapterURL", headers)).execute().asJsoup()
        if (aChapterDocument.select("p.detail-block-content").hasText()) manga.status = SManga.LICENSED

        return manga
    }

    // Chapters

    private fun chapterListSelector() = "ul.detail-main-list > li"

    private fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        chapter.name = element.selectFirst("a p.title3")!!.text()
        chapter.date_upload = element.selectFirst("a p.title2")?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    private fun parseChapterDate(date: String): Long = if ("Today" in date || " ago" in date) {
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
        dateFormat.tryParse(date)
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
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

            urls.mapIndexed { index, s -> Page(index, imageUrl = "https:$s") }
        } else {
            val html = document.html()
            val link = document.location()

            var secretKey = extractSecretKey(html, quickJs)

            val chapterIdStartLoc = html.indexOf("chapterid")
            val chapterId = html.substring(
                chapterIdStartLoc + 11,
                html.indexOf(";", chapterIdStartLoc),
            ).trim()

            val chapterPagesElement = document.selectFirst(".pager-list-left > span")!!
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

                    val pageResponse = notRateLimitClient.newCall(request).execute()
                    responseText = pageResponse.body.string()

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

                Page(i - 1, imageUrl = "https:$baseLink$imageLink")
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

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        TypeList(types.keys.toList().sorted().toTypedArray()),
        ArtistFilter("Artist"),
        AuthorFilter("Author"),
        GenreList(genres()),
        RatingList(ratings),
        YearFilter("Year released"),
        CompletionList(completions),
    )
}
