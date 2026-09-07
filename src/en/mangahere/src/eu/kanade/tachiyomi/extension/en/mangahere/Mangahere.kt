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
    override fun OkHttpClient.Builder.configureClient() = apply {
        addCookie("isAdult" to "1")
        rateLimit(1, 2.seconds) {
            it.host == baseUrl.toHttpUrl().host && !it.encodedPath.endsWith("/chapterfun.ashx")
        }
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

        // if-branch is for webtoon reader, else is for page-by-page
        return if (bar.isNotEmpty()) {
            val script = document.select("script:containsData(function(p,a,c,k,e,d))").html().removePrefix("eval")
            val deobfuscatedScript = QuickJs.create().use { it.evaluate(script).toString() }
            val urls = deobfuscatedScript.substringAfter("newImgs=['").substringBefore("'];").split("','")

            urls.mapIndexed { index, s -> Page(index, imageUrl = "https:$s") }
        } else {
            val html = document.html()
            val link = document.location()

            val secretKey = QuickJs.create().use { extractSecretKey(html, it) }

            val chapterIdStartLoc = html.indexOf("chapterid")
            val chapterId = html.substring(
                chapterIdStartLoc + 11,
                html.indexOf(";", chapterIdStartLoc),
            ).trim()

            val chapterPagesElement = document.selectFirst(".pager-list-left > span")!!
            val pagesLinksElements = chapterPagesElement.select("a")
            val pagesNumber = pagesLinksElements[pagesLinksElements.size - 2].attr("data-page").toInt()

            val pageBase = link.substring(0, link.lastIndexOf("/"))
            IntRange(1, pagesNumber).map { page ->
                val pageUrl = "$pageBase/chapterfun.ashx".toHttpUrl().newBuilder()
                    .addQueryParameter("cid", chapterId)
                    .addQueryParameter("page", page.toString())
                    .addQueryParameter("key", secretKey)
                    .fragment(link)
                    .build()

                Page(page - 1, url = pageUrl.toString())
            }
        }
            .dropLastIfBroken()
    }

    /*
        function to drop last imageUrl if it's broken/unneccesary, working imageUrls are incremental (e.g. t001, t002, etc); if the difference between
        the last two isn't 1 or doesn't have an Int at the end of the last imageUrl's filename, drop last Page
     */
    private suspend fun List<Page>.dropLastIfBroken(): List<Page> {
        if (size < 2) return this

        val resolvedPages = mapIndexed { index, page ->
            if (index < lastIndex - 1 || page.imageUrl != null) {
                page
            } else {
                page.apply { imageUrl = getImageUrl(this) }
            }
        }
        val pageNumbers = resolvedPages.takeLast(2).map { page ->
            page.imageUrl
                ?.substringBeforeLast(".")
                ?.substringAfterLast("/")
                ?.takeLast(2)
                ?.toIntOrNull()
                ?: return resolvedPages.dropLast(1)
        }

        return if (
            pageNumbers[1] - pageNumbers[0] == 1 ||
            (pageNumbers[0] == 0 && pageNumbers[1] == 99)
        ) {
            resolvedPages
        } else {
            resolvedPages.dropLast(1)
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val pageUrl = page.url.toHttpUrl()
        val referer = pageUrl.fragment ?: error("Missing chapter referer")
        val requestUrl = pageUrl.newBuilder().fragment(null).build()
        val pageHeaders = headers.newBuilder()
            .set("Referer", referer)
            .set("Accept", "*/*")
            .set("Accept-Language", "en-US,en;q=0.9")
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        var responseText: String
        for (attempt in 0..2) {
            val url = if (attempt == 0) {
                requestUrl
            } else {
                requestUrl.newBuilder().setQueryParameter("key", "").build()
            }
            responseText = client.get(url, pageHeaders).use { it.body.string() }
            if (responseText.isNotEmpty()) {
                return parseImageUrl(responseText)
            }
        }

        error("Empty image response")
    }

    private fun parseImageUrl(responseText: String): String {
        val deobfuscatedScript = QuickJs.create().use {
            it.evaluate(responseText.removePrefix("eval")).toString()
        }

        val baseLinkStart = deobfuscatedScript.indexOf("pix=")
            .takeIf { it >= 0 }
            ?.plus(5)
            ?: error("Missing image host")
        val baseLinkEnd = deobfuscatedScript.indexOf(";", baseLinkStart)
            .takeIf { it > baseLinkStart }
            ?.minus(1)
            ?: error("Invalid image host")
        val baseLink = deobfuscatedScript.substring(baseLinkStart, baseLinkEnd)

        val imageLinkStart = deobfuscatedScript.indexOf("pvalue=")
            .takeIf { it >= 0 }
            ?.plus(9)
            ?: error("Missing image path")
        val imageLinkEnd = deobfuscatedScript.indexOf('"', imageLinkStart)
            .takeIf { it > imageLinkStart }
            ?: error("Invalid image path")
        val imageLink = deobfuscatedScript.substring(imageLinkStart, imageLinkEnd)

        return "https:$baseLink$imageLink"
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
