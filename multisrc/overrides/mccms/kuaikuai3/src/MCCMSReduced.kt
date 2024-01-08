package eu.kanade.tachiyomi.extension.zh.kuaikuai3

import eu.kanade.tachiyomi.multisrc.mccms.DecryptInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Evaluator

open class MCCMSReduced(
    override val name: String,
    override val baseUrl: String,
) : HttpSource() {
    override val lang = "zh"
    override val supportsLatest get() = false

    override val client by lazy {
        network.client.newBuilder()
            .rateLimitHost(baseUrl.toHttpUrl(), 2)
            .addInterceptor(DecryptInterceptor)
            .build()
    }

    private fun searchOnly(): Nothing = throw Exception("此图源只支持搜索")
    private val noWebView = "https://stevenyomi.github.io/echo#本图源不支持网页查看"

    override fun popularMangaRequest(page: Int) = searchOnly()
    override fun popularMangaParse(response: Response) = searchOnly()
    override fun latestUpdatesRequest(page: Int) = searchOnly()
    override fun latestUpdatesParse(response: Response) = searchOnly()

    override fun getMangaUrl(manga: SManga) = noWebView
    override fun getChapterUrl(chapter: SChapter) = noWebView

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.php/search".toHttpUrl().newBuilder()
            .addQueryParameter("key", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val placeholder = "$baseUrl/template/pc/default/images/bg_loadimg_3x4.png"
        val entries = document.select(Evaluator.Tag("a")).map { link ->
            SManga.create().apply {
                url = link.attr("href")
                title = link.ownText()
                thumbnail_url = placeholder
            }
        }
        return MangasPage(entries, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val metaProperties = HashMap<String, String>()
        for (element in document.head().children()) {
            if (element.tagName() == "meta" && element.hasAttr("property")) {
                val key = element.attr("property").removePrefix("og:")
                metaProperties[key] = element.attr("content")
            }
        }
        return SManga.create().apply {
            title = metaProperties["title"]!!
            author = metaProperties["novel:author"]
            description = metaProperties["description"]
            val statusText = metaProperties["novel:status"]
            status = when {
                statusText == null -> SManga.UNKNOWN
                '连' in statusText -> SManga.ONGOING
                '完' in statusText -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = metaProperties["image"]
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(Evaluator.Class("j-chapter-link")).asReversed().map { link ->
            SChapter.create().apply {
                url = link.attr("href")
                name = link.ownText()
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(Evaluator.Tag("img")).mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("data-original"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}
