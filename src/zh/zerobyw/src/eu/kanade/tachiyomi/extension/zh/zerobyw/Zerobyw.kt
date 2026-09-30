package eu.kanade.tachiyomi.extension.zh.zerobyw

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class Zerobyw : KeiSource() {

    override val supportsLatest = false

    private val preferences = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(UpdateUrlInterceptor(preferences))

    override fun Headers.Builder.configureHeaders() = set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0")

    // Popular
    // Website does not provide popular manga, this is actually latest manga

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = browseUrlBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return fetchMangaList(url)
    }

    private suspend fun fetchMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
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

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return fetchMangaList(builder.build())
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val labs = document
            .select("main div.flex-wrap.text-sm > span")
            .eachText()
        val details = SManga.create().apply {
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
        val chapterList = document.select("div.grid a[href*=/view/index.php]").map { element: Element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text()
            }
        }.asReversed()
        return SMangaUpdate(details, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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

    // Filters

    override fun getFilterList(data: JsonElement?) = FilterList(
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
}
