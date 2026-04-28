package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.network.GET
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
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)

open class NineManga(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest: Boolean = true

    private val cookieInterceptor = CookieInterceptor(baseUrl.substringAfter("://"), "ninemanga_list_num" to "1")

    private val imgNiaddRegex = """img\d.\.niadd.com""".toRegex()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains(imgNiaddRegex)) {
                val newRequest = request.newBuilder()
                    .addHeader("Referer", "$baseUrl/")
                    .build()
                return@addInterceptor chain.proceed(newRequest)
            }
            chain.proceed(request)
        }
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept-Language", "es-ES,es;q=0.9,en;q=0.8,gl;q=0.7")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/75")

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/New-Update/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("dl.bookinfo").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.select("ul.pageList > li:last-child > a.l").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.bookname")?.let {
            url = it.attr("abs:href").substringAfter(baseUrl)
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/index_$page.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("dl.bookinfo").map { popularMangaFromElement(it) }
        val hasNextPage = document.select("ul.pageList > li:last-child > a.l").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()

        url.addQueryParameter("wd", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is QueryCBEFilter -> url.addQueryParameter("name_sel", filter.toUriPart())
                is AuthorCBEFilter -> url.addQueryParameter("author_sel", filter.toUriPart())
                is AuthorFilter -> url.addQueryParameter("author", filter.state)
                is ArtistCBEFilter -> url.addQueryParameter("artist_sel", filter.toUriPart())
                is ArtistFilter -> url.addQueryParameter("artist", filter.state)
                is GenreList -> {
                    val genreInclude = filter.state.filter { it.isIncluded() }.joinToString("") { "${it.id}," }
                    val genreExclude = filter.state.filter { it.isExcluded() }.joinToString("") { "${it.id}," }
                    url.addQueryParameter("category_id", genreInclude)
                    url.addQueryParameter("out_category_id", genreExclude)
                }
                is CompletedFilter -> url.addQueryParameter("completed_series", filter.toUriPart())
                else -> {}
            }
        }

        url.addQueryParameter("type", "high")

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("dl.bookinfo").map { searchMangaFromElement(it) }
        val hasNextPage = document.select("ul.pageList > li:last-child > a.l").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            document.selectFirst("div.bookintro")?.let {
                title = it.select("li > span:not([class])").text().removeSuffix(" Manga")
                genre = it.select("li[itemprop=genre] a").joinToString { e -> e.text() }
                author = it.select("li a[itemprop=author]").text()
                status = parseStatus(it.selectFirst("li a.red")?.text().orEmpty())
                description = it.select("p[itemprop=description]").text()
                thumbnail_url = it.selectFirst("img[itemprop=image]")?.attr("abs:src")
            }
        }
    }

    open fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "?waring=1", headers) // Bypasses adult content warning
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaTitle = document.selectFirst("div.bookintro li > span:not([class])")?.text()?.removeSuffix(" Manga") ?: ""
        val titleForCleaning = "$mangaTitle "

        return document.select("ul.sub_vol_ul > li").map { element ->
            SChapter.create().apply {
                element.selectFirst("a.chapter_list_a")?.let {
                    name = it.text().replace(titleForCleaning, "", ignoreCase = true)
                    url = it.attr("abs:href").substringAfter(baseUrl).replace("%20", " ")
                }
                date_upload = parseChapterDate(element.select("span").text())
            }
        }
    }

    open fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if (dateWords[1].contains(",")) {
                return dateFormat.tryParse(date)
            } else {
                val timeAgo = dateWords[0].toIntOrNull() ?: return 0L
                val calField = when (dateWords[1]) {
                    "minutes" -> Calendar.MINUTE
                    "hours" -> Calendar.HOUR
                    else -> return 0L
                }
                return Calendar.getInstance().apply {
                    add(calField, -timeAgo)
                }.timeInMillis
            }
        }
        return 0L
    }

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    open fun pageListParse(document: Document): List<Page> = document.select("select#page").first()?.select("option")?.mapIndexed { index, element ->
        Page(index, url = baseUrl + element.attr("value"))
    } ?: emptyList()

    override fun imageUrlParse(response: Response): String = imageUrlParse(response.asJsoup())

    open fun imageUrlParse(document: Document): String = document.select("div.pic_box img.manga_pic").first()?.attr("abs:src").orEmpty()

    override fun getFilterList() = FilterList(
        QueryCBEFilter(),
        AuthorCBEFilter(),
        AuthorFilter(),
        ArtistCBEFilter(),
        ArtistFilter(),
        GenreList(getGenreList()),
        CompletedFilter(),
    )

    open fun getGenreList(): List<Genre> = enGenres
}
