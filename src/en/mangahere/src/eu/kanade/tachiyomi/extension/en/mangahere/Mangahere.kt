package eu.kanade.tachiyomi.extension.en.mangahere

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Mangahere : KeiSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private val notRateLimitClient: OkHttpClient = network.client.newBuilder()
        .addCookie("isAdult" to "1")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("isAdult" to "1")
        rateLimit(1, 2.seconds) { it.host == baseUrlHost }
    }

    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd,yyyy", Locale.ENGLISH)

    // Popular Manga

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/directory/$page.htm").asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/directory/$page.htm?latest").asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst(latestUpdatesNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun latestUpdatesSelector() = ".manga-list-1-list li"

    private fun latestUpdatesNextPageSelector() = "div.pager-list-left a:last-child"

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

        val document = client.get(url.build()).asJsoup()
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedManga = mangaDetailsFromDocument(document)
        val updatedChapters = chapterListFromDocument(document)

        // Explicitly ignoring `fetchChapters` as we would otherwise provide incomplete data
        // Get a chapter, check if the manga is licensed.
        val aChapterUrl = getChapterUrl(updatedChapters.first())
        val aChapterDocument = client.get(aChapterUrl).asJsoup()
        if (aChapterDocument.select("p.detail-block-content").hasText()) updatedManga.status = SManga.LICENSED

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun mangaDetailsFromDocument(document: Document): SManga {
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

    private fun chapterListFromDocument(document: Document): List<SChapter> = document.select(chapterListSelector()).map { chapterFromElement(it) }

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
        dateFormat.tryParseDate(date)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
            val pageHeaders = Headers.Builder()
                .set("Referer", link)
                .set("Accept", "*/*")
                .set("Accept-Language", "en-US,en;q=0.9")
                .set("Connection", "keep-alive")
                .set("Host", "www.mangahere.cc")
                .set("User-Agent", System.getProperty("http.agent") ?: "")
                .set("X-Requested-With", "XMLHttpRequest")
                .build()

            IntRange(1, pagesNumber).map { i ->
                val pageLink = "$pageBase/chapterfun.ashx?cid=$chapterId&page=$i&key=$secretKey"

                var responseText = ""

                for (tr in 1..3) {
                    responseText = notRateLimitClient.get(pageLink, pageHeaders).use { it.body.string() }

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

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeList(types.keys.toList().sorted().toTypedArray()),
        ArtistFilter("Artist"),
        AuthorFilter("Author"),
        GenreList(genres()),
        RatingList(ratings),
        YearFilter("Year released"),
        CompletionList(completions),
    )
}
