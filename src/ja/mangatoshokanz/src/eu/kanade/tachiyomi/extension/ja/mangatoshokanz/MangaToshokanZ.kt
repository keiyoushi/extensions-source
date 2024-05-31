package eu.kanade.tachiyomi.extension.ja.mangatoshokanz

import android.util.Base64
import eu.kanade.tachiyomi.lib.cryptoaes.CryptoAES
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.lang.StringBuilder
import java.security.KeyPair
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

class MangaToshokanZ : HttpSource() {
    override val lang = "ja"
    override val supportsLatest = true
    override val name = "マンガ図書館Z"
    override val baseUrl = "https://www.mangaz.com"

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::mangaDetailInterceptor)
        .rateLimit(1, 3, TimeUnit.SECONDS)
        .build()

    private val json: Json by injectLazy()

    private val keys: KeyPair by lazy {
        getKeys()
    }

    private val _serial by lazy {
        getSerial()
    }

    private fun mangaDetailInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.pathSegments.first() == "book" && response.code == 404) {
            val id = request.url.pathSegments.last().toInt() + 1
            val url = request.url.toString().substringBeforeLast("/").plus("/$id")

            return client.newCall(GET(url, headers)).execute()
        }
        return response
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("cookie", "_LANG_=ja")

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegments("ranking/views")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst(".itemList")!!.children().mangasFromListElements()

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
        val document = response.asJsoup()
        val mangas = document.selectFirst("body")!!.children().mangasFromListElements()

        return MangasPage(mangas, mangas.size == LATEST_MANGA_COUNT_PER_PAGE)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("title/index")
            .addQueryParameter("query", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst(".itemList")!!.children().filter { child ->
            child.`is`("li")
        }.mangasFromListElements()

        return MangasPage(mangas, false)
    }

    private fun List<Element>.mangasFromListElements(): List<SManga> {
        return map { li ->
            SManga.create().apply {
                val a = li.selectFirst("h4 > a")!!
                url = a.attr("href").substringAfterLast("/")
                title = a.text()

                val img = li.selectFirst("a > img")!!
                thumbnail_url = if (img.hasAttr("src")) {
                    img.attr("src")
                } else {
                    img.attr("data-src")
                }
            }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("book/detail")
            .addPathSegment(manga.url)
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
                    li.ownText().contains("作画") || li.ownText().contains("マンガ")-> {
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
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("series/detail")
            .addPathSegment(manga.url)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        if (response.priorResponse?.code == 302) {
            return listOf(
                SChapter.create().apply {
                    name = document.selectFirst(".GA4_booktitle")!!.text()
                    url = document.baseUri().substringAfterLast("/")
                    chapter_number = 1f
                    date_upload = 0
                },
            )
        }

        return document.select(".itemList li").reversed().mapIndexed { i, li ->
            SChapter.create().apply {
                name = li.selectFirst(".title")!!.text()
                url = li.selectFirst("a")!!.attr("href").substringAfterLast("/")
                chapter_number = i.toFloat()
                date_upload = 0
            }
        }.reversed()

        /*
        val seriesName = document.select(".topicPath a").last()!!.text()
        var isSingleChapter: Boolean
        val chapters = mutableListOf(
            // first chapter
            SChapter.create().apply {
                name = document.selectFirst(".GA4_booktitle")!!.text()
                url = document.baseUri().substringAfterLast("/")
                chapter_number = 1f
                date_upload = 0

                isSingleChapter = name == seriesName
            },
        )

        if (isSingleChapter.not()) {
            chapters.addAll(
                // next chapters in the series
                document.selectFirst(".itemList")!!.let { itemList ->
                    itemList.children().mapIndexed { i, li ->
                        SChapter.create().apply {
                            val a = li.selectFirst("h4 > a")!!

                            name = if (a.text().endsWith("")) {
                                seriesName.plus(" ").plus(i + 1)
                            } else {
                                a.text()
                            }

                            //name = (i + 1).toString()
                            url = a.attr("href").substringAfterLast("/")
                            chapter_number = (i + 1).toFloat()
                            date_upload = 0
                        }
                    }
                },
            )
        }

        return chapters.reversed()

         */
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val ticket = getTicket(chapter.url)
        val publicPem = keys.public.toPem()

        val url = virgoBuilder()
            .addPathSegment("docx")
            .addPathSegment(chapter.url.plus(".json"))
            .build()

        val header = headers.newBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Cookie", "virgo!__ticket=$ticket")
            .build()


        val body1 = FormBody.Builder()
            .add("__serial", _serial)
            .add("__ticket", ticket)
            .add("pub", publicPem)
            .build()


        val text = "__serial=$_serial&__ticket=$ticket&pub=$publicPem"
        val body = RequestBody.create("application/x-www-form-urlencoded".toMediaTypeOrNull(), text)

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

        val response = client.newCall(ticketRequest).execute()

        return response.headers.values("Set-Cookie")
            .find { it.contains("virgo!__ticket") }!!
            .substringAfter("virgo!__ticket=")
            .substringBefore(";")
    }

    private fun getSerial(): String {
        val url = virgoBuilder()
            .addPathSegment("app.js")
            .build()

        val response = client.newCall(GET(url, headers)).execute()
        val appJsString = response.body.string()

        return appJsString.substringAfter("__serial = \"").substringBefore("\";")
    }

    private fun virgoBuilder(): HttpUrl.Builder {
        return baseUrl.toHttpUrl().newBuilder()
            .host("vw.mangaz.com")
            .addPathSegment("virgo")
    }

    override fun pageListParse(response: Response): List<Page> {
        val encrypted = json.decodeFromString<Encrypted>(response.body.string())

        val biBase64Decoded = Base64.decode(encrypted.bi, Base64.DEFAULT)
        val ekBase64Decoded = Base64.decode(encrypted.ek, Base64.DEFAULT)

        val ekPrivateKeyDecrypted = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
            init(Cipher.DECRYPT_MODE, keys.private)
            doFinal(ekBase64Decoded)
        }

        val dataDecrypted = CryptoAES.decrypt(encrypted.data, ekPrivateKeyDecrypted, biBase64Decoded)
        val decrypted = json.decodeFromString<Decrypted>(dataDecrypted)

        return decrypted.images.mapIndexed { i, image ->
            val url = StringBuilder(decrypted.location.base)
                .append(decrypted.location.st)

            if (image.file.endsWith("jpg").not()) {
                url.append(image.file.substringBefore("."))
                url.append(".jpg")
            } else {
                url.append(image.file)
            }

            Page(i, imageUrl = url.toString())
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        const val LATEST_MANGA_COUNT_PER_PAGE = 50
    }
}
