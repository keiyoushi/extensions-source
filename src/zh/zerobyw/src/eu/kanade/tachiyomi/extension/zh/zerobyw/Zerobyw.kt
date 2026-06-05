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

    override val client = network.client.newBuilder()
        .addInterceptor(UpdateUrlInterceptor(preferences))
        .build()

    override val baseUrl
        get() = when {
            isCi -> ciGetUrl(client)
            else -> preferences.baseUrl
        }

    private val isCi = System.getenv("CI") == "true"

    override fun headersBuilder() = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")

    // Popular
    // Website does not provide popular manga, this is actually latest manga

    override fun popularMangaRequest(page: Int): Request {
        val url = browseUrlBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("a[href*=/details/?kuid=]").map { element: Element ->
            parseMangaFromCard(element)
        }
        val hasNextPage = document.selectFirst("a:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseMangaFromCard(element: Element): SManga = SManga.create().apply {
        title = getTitle(element.selectFirst("h3")!!.text())
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = element.selectFirst("img")!!.absUrl("src")
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builder = browseUrlBuilder()
        if (query.isNotBlank()) {
            builder.addQueryParameter("keyword", query)
        } else {
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
        val mangas = document.select("a[href*=/details/?kuid=]").map { element: Element ->
            parseMangaFromCard(element)
        }
        val hasNextPage = document.selectFirst("a:contains(下一页)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val labs = document
            .select("main div.flex-wrap.text-sm > span")
            .eachText()
        return SManga.create().apply {
            title = getTitle(document.selectFirst("main h1")!!.text())
            thumbnail_url = document.selectFirst("main img.object-contain")!!.absUrl("src")
            author = labs.firstOrNull()?.removePrefix("作者: ")
            genre = labs.joinToString(", ")
            description = document.selectFirst("p[x-ref=summaryText]")?.html()?.replace("<br>", "")
            status = when {
                labs.any { it == "连载中" } -> SManga.ONGOING
                labs.any { it == "已完结" } -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.grid a[href*=/view/index.php]").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
            }
        }.asReversed()
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("#image-container img.manga-image")
        if (images.isEmpty()) {
            var message = document.select("div#messagetext > p")
            if (message.isEmpty()) {
                message = document.select("main + div p")
            }
            if (message.isNotEmpty()) {
                error(message.text())
            }
        }
        return images.mapIndexed { index: Int, img: Element ->
            Page(index, imageUrl = img.absUrl("src"))
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

    private fun browseUrlBuilder() = "$baseUrl/pc/pc/".toHttpUrl().newBuilder()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(getBaseUrlPreference(screen.context))
    }
}
