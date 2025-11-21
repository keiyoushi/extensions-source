package eu.kanade.tachiyomi.extension.all.xkcd

import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptor
import eu.kanade.tachiyomi.lib.textinterceptor.TextInterceptorHelper
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

open class Xkcd(
    final override val baseUrl: String,
    final override val lang: String,
    dateFormat: String = "yyyy-MM-dd",
) : HttpSource() {
    final override val name = "xkcd"

    final override val supportsLatest = false
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(TextInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.host != "thumbnail") return@addInterceptor chain.proceed(request)

            val image = this::class.java
                .getResourceAsStream("/assets/thumbnail.png")!!
                .readBytes()
            val responseBody = image.toResponseBody("image/png".toMediaType())
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(responseBody)
                .build()
        }
        .build()

    protected open val archive = "/archive"

    protected open val creator = "Randall Munroe"

    protected open val synopsis =
        "A webcomic of romance, sarcasm, math and language."

    protected open val interactiveText =
        "To experience the interactive version of this comic, open it in WebView/browser."

    protected open val chapterListSelector = "#middleContainer > a"

    protected open val imageSelector = "#comic > img"

    private val dateFormat = SimpleDateFormat(dateFormat, Locale.ROOT)

    protected fun String.timestamp() = dateFormat.parse(this)?.time ?: 0L

    protected open fun String.numbered(number: Any) = "$number - $this"

    private fun makeSManga(): SManga =
        SManga.create().apply {
            title = name
            artist = creator
            author = creator
            description = synopsis
            status = SManga.ONGOING
            thumbnail_url = "https://thumbnail/xkcd.png"
            setUrlWithoutDomain(archive)
        }

    final override fun fetchPopularManga(page: Int) =
        Observable.just(MangasPage(listOf(makeSManga()), false))

    final override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
        Observable.just(MangasPage(emptyList(), false))!!

    final override fun fetchMangaDetails(manga: SManga) =
        Observable.just(makeSManga())!!

    override fun chapterListParse(response: Response) =
        response.asJsoup().select(chapterListSelector).map {
            SChapter.create().apply {
                url = it.attr("href")
                val number = url.removeSurrounding("/")
                name = it.text().numbered(number)
                chapter_number = number.toFloat()
                date_upload = it.attr("title").timestamp()
            }
        }

    override fun pageListParse(response: Response): List<Page> {
        // if the img tag is empty or has siblings then it is an interactive comic
        val img = response.asJsoup().selectFirst(imageSelector)?.takeIf {
            it.nextElementSibling() == null
        } ?: error(interactiveText)

        // if an HD image is available it'll be the srcset attribute
        val image = when {
            !img.hasAttr("srcset") -> img.attr("abs:src")
            else -> img.attr("abs:srcset").substringBefore(' ')
        }

        // create a text image for the alt text
        val text = TextInterceptorHelper.createUrl(img.attr("alt"), img.attr("title"))

        return listOf(Page(0, "", image), Page(1, "", text))
    }

    final override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun latestUpdatesParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun latestUpdatesRequest(page: Int) =
        throw UnsupportedOperationException()

    final override fun mangaDetailsParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun popularMangaParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun popularMangaRequest(page: Int) =
        throw UnsupportedOperationException()

    final override fun searchMangaParse(response: Response) =
        throw UnsupportedOperationException()

    final override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        throw UnsupportedOperationException()
}
