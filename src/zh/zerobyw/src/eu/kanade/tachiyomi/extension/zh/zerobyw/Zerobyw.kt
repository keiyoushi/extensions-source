package eu.kanade.tachiyomi.extension.zh.zerobyw

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Zerobyw :
    HttpSource(),
    ConfigurableSource {

    override val name: String = "zero搬运网"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = false

    private val preferences = getPreferences { clearOldBaseUrl() }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(UpdateUrlInterceptor(preferences))
        .build()

    override val baseUrl get() = when {
        isCi -> ciGetUrl(client)
        else -> preferences.baseUrl
    }

    private val isCi = System.getenv("CI") == "true"

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")

    // Popular
    // Website does not provide popular manga, this is actually latest manga

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/plugin.php?id=jameson_manhua&c=index&a=ku&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.uk-card").map { element: Element ->
            parseMangaFromCard(element)
        }
        val hasNextPage = document.selectFirst("div.pg > a.nxt") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaFromCard(element: Element): SManga {
        val link = element.selectFirst("p.mt5 > a")!!
        return SManga.create().apply {
            title = getTitle(link.text())
            setUrlWithoutDomain(link.absUrl("href"))
            thumbnail_url = element.selectFirst("img")!!.attr("src")
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = "$baseUrl/plugin.php".toHttpUrl().newBuilder()
            .addEncodedQueryParameter("id", "jameson_manhua")
        if (query.isNotBlank()) {
            builder
                .addEncodedQueryParameter("a", "search")
                .addEncodedQueryParameter("c", "index")
                .addQueryParameter("keyword", query)
        } else {
            builder
                .addEncodedQueryParameter("c", "index")
                .addEncodedQueryParameter("a", "ku")
            filters.forEach {
                if (it is UriSelectFilterPath && it.toUri().second.isNotEmpty()) {
                    builder.addQueryParameter(it.toUri().first, it.toUri().second)
                }
            }
        }
        builder.addEncodedQueryParameter("page", page.toString())
        return GET(builder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a.uk-card, div.uk-card").map { element: Element ->
            SManga.create().apply {
                title = getTitle(element.selectFirst("p.mt5")!!.text())
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")!!.attr("src")
            }
        }
        val hasNextPage = document.selectFirst("div.pg > a.nxt") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = getTitle(document.selectFirst("h3.uk-heading-line")!!.text())
            thumbnail_url = document.selectFirst("div.uk-width-medium > img")!!.absUrl("src")
            author = document.selectFirst("div.cl > a.uk-label")!!.text().substring(3)
            genre = document.select("div.cl > a.uk-label, div.cl > span.uk-label").eachText().joinToString(", ")
            description = document.select("li > div.uk-alert").html().replace("<br>", "")
            status = when (document.select("div.cl > span.uk-label").last()!!.text()) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.uk-grid-collapse > div.muludiv").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.uk-button-default")!!.absUrl("href"))
                name = element.selectFirst("a.uk-button-default")!!.text()
            }
        }.asReversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div.uk-text-center > img")
        if (images.isEmpty()) {
            var message = document.select("div#messagetext > p")
            if (message.isEmpty()) {
                message = document.select("div.uk-alert > p")
            }
            if (message.isNotEmpty()) {
                error(message.text())
            }
        }
        return images.mapIndexed { index: Int, img: Element ->
            Page(index, imageUrl = img.attr("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList() = FilterList(
        eu.kanade.tachiyomi.source.model.Filter.Header("如果使用文本搜索"),
        eu.kanade.tachiyomi.source.model.Filter.Header("过滤器将被忽略"),
        CategoryFilter(),
        StatusFilter(),
        AttributeFilter(),
    )

    // Helpers

    private companion object {
        val commentRegex = Regex("【\\d+")
    }

    private fun getTitle(title: String): String {
        val result = commentRegex.find(title)
        return if (result != null) {
            title.substring(0, result.range.first)
        } else {
            title.substringBefore('【')
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(getBaseUrlPreference(screen.context))
    }
}
