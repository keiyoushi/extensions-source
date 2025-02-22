package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.lang.StringBuilder
import java.security.KeyPair

class MangaToshokanZ : HttpSource() {
    override val lang = "ja"
    override val supportsLatest = true
    override val name = "マンガ図書館Z"
    override val baseUrl = "https://www.mangaz.com"

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::r18Interceptor)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        // author/illustrator name might just show blank if language not set to japan
        .add("cookie", "_LANG_=ja")

    private val keys: KeyPair by lazy {
        getKeys()
    }

    private val _serial by lazy {
        getSerial()
    }

    private var isR18 = false

    private fun r18Interceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // open access to R18 section
        if (request.url.host == "r18.mangaz.com" && isR18.not()) {
            val url = "https://r18.mangaz.com/attention/r18/yes"

            val r18Request = Request.Builder()
                .url(url)
                .head()
                .build()

            isR18 = true
            client.newCall(r18Request).execute().close()
        }

        return chain.proceed(request)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/ranking/views", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.toMangas(".itemList")
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val header = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/addpage_renewal")
            .addQueryParameter("type", "official")
            .addQueryParameter("sort", "new")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, header)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.toMangas("body")
        return MangasPage(mangas, mangas.size == 50)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val header = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/addpage_renewal")
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is Category -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("category", categories[filter.state].lowercase())
                    }
                    if (filter.state == 5) {
                        url.host("r18.mangaz.com")
                    }
                }
                is Sort -> {
                    url.addQueryParameter("sort", sortBy[filter.state].lowercase())
                }
                else -> {}
            }
        }

        return GET(url.build(), header)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    private fun Response.toMangas(selector: String): List<SManga> {
        return asJsoup().selectFirst(selector)!!.children().filter { child ->
            child.`is`("li")
        }.filterNot { li ->
            // discard manga that in the middle of asking for license progress, it can't be read
            li.selectFirst(".iconConsent") != null
        }.map { li ->
            SManga.create().apply {
                val a = li.selectFirst("h4 > a")!!
                url = a.attr("href").substringAfterLast("/")
                title = a.text()

                thumbnail_url = li.selectFirst("a > img")!!.attr("data-src").ifBlank {
                    li.selectFirst("a > img")!!.attr("src")
                }
            }
        }
    }

    override fun getFilterList() = FilterList(Category(), Sort())

    private class Category : Filter.Select<String>("Category", categories)

    private class Sort : Filter.Select<String>("Sort", sortBy)

    // in this manga details section we use book/detail/id since it have tags over series/detail/id
    override fun mangaDetailsRequest(manga: SManga): Request {
        // normally manga published by the website has the same id in it's series and book
        // example: https://www.mangaz.com/series/detail/202371 (series)
        //          https://www.mangaz.com/book/detail/202371 (book)
        // strangely manga published by registered user has different id in it's series and book
        // example: https://www.mangaz.com/series/detail/224931 (series)
        //          https://www.mangaz.com/book/detail/224932 (book)

        // so in here we want the id from the manga thumbnail url since it contain the book id
        // instead of manga url that contain series id which used for the chapter section later
        // example: https://www.mangaz.com/series/detail/224931 (manga url)
        //          https://books.j-comi.jp/Books/224/224932/thumb160_1713230205.jpg (thumbnail url)
        val bookId = manga.thumbnail_url!!.substringBeforeLast("/").substringAfterLast("/")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("book/detail")
            .addPathSegment(bookId)
            .build()

        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            document.select(".detailAuthor > li").forEach { li ->
                when {
                    li.ownText().contains("者") || li.ownText().contains("原作") -> {
                        if (author.isNullOrEmpty()) {
                            author = li.child(0).text()
                        } else {
                            author += ", ${li.child(0).text()}"
                        }
                    }
                    li.ownText().contains("作画") || li.ownText().contains("マンガ") -> {
                        if (artist.isNullOrEmpty()) {
                            artist = li.child(0).text()
                        } else {
                            artist += ", ${li.child(0).text()}"
                        }
                    }
                }
            }
            description = document.selectFirst(".wordbreak")?.text()
            genre = document.select(".inductionTags a").joinToString { it.text() }
            status = when {
                document.selectFirst("p.iconContinues") != null -> SManga.ONGOING
                document.selectFirst("p.iconEnd") != null -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // we want series/detail/id over book/detail/id in here since book/detail/id have problem
    // where if the name of the chapter become too long the end become ellipsis (...)
    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("series/detail")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        // if it's single chapter, it will be redirected back to book/detail/id
        if (response.request.url.pathSegments.first() == "book") {
            return listOf(
                SChapter.create().apply {
                    name = document.selectFirst(".GA4_booktitle")!!.text()
                    url = document.baseUri().substringAfterLast("/")
                    chapter_number = 1f
                    date_upload = 0
                },
            )
        }

        // if it's multiple chapters
        return document.select(".itemList li").reversed().mapIndexed { i, li ->
            SChapter.create().apply {
                name = li.selectFirst(".title")!!.text()
                url = li.selectFirst("a")!!.attr("href").substringAfterLast("/")
                chapter_number = i.toFloat()
                date_upload = 0
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val ticket = getTicket(chapter.url)
        val pem = keys.public.toPem()

        val url = virgoBuilder()
            .addPathSegment("docx")
            .addPathSegment(chapter.url.plus(".json"))
            .build()

        val header = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Cookie", "virgo!__ticket=$ticket")
            .build()

        val body = FormBody.Builder()
            .add("__serial", _serial)
            .add("__ticket", ticket)
            .add("pub", pem)
            .build()

        return POST(url.toString(), header, body)
    }

    private fun getTicket(chapterId: String): String {
        val ticketUrl = virgoBuilder()
            .addPathSegments("view")
            .addPathSegment(chapterId)
            .build()

        val ticketRequest = Request.Builder()
            .url(ticketUrl)
            .headers(headers)
            .head()
            .build()

        try {
            client.newCall(ticketRequest).execute().close()
        } catch (_: Exception) {
            throw Exception("Fail to retrieve ticket")
        }

        return client.cookieJar.loadForRequest(ticketUrl).find { cookie ->
            cookie.name == "virgo!__ticket"
        }?.value ?: throw Exception("Fail to retrieve ticket from cookie")
    }

    private fun getSerial(): String {
        val url = virgoBuilder()
            .addPathSegment("app.js")
            .build()

        val response = try {
            client.newCall(GET(url, headers)).execute()
        } catch (_: Exception) {
            throw Exception("Fail to retrieve serial")
        }

        val appJsString = response.body.string()
        return appJsString.substringAfter("__serial = \"").substringBefore("\";")
    }

    private fun virgoBuilder(): HttpUrl.Builder {
        return baseUrl.toHttpUrl().newBuilder()
            .host("vw.mangaz.com")
            .addPathSegment("virgo")
    }

    override fun pageListParse(response: Response): List<Page> {
        val decrypted = response.decryptPages(keys.private)

        return decrypted.images.mapIndexed { i, image ->
            val imageUrl = StringBuilder(decrypted.location.base)
                .append(decrypted.location.st)
                .append(image.file.substringBefore("."))
                .append(".jpg")

            Page(i, imageUrl = imageUrl.toString())
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        private val categories = arrayOf(
            "All",
            "Mens",
            "Womens",
            "TL",
            "BL",
            "R18",
        )
        private val sortBy = arrayOf(
            "Popular",
            "New",
        )
    }
}
