package eu.kanade.tachiyomi.extension.ja.comicmeteor

import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class ComicMeteor : HttpSource() {

    override val name = "Kiraboshi"
    override val baseUrl = "https://kirapo.jp"
    override val lang = "ja"
    override val supportsLatest = false
    override val versionId = 2

    private val apiUrl = "https://kirapo.jp/api"
    private val json = Injekt.get<Json>()
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    private var readAtTimestamp: String? = null
    private var allFiltersList: List<FilterOption> = emptyList()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .apply {
            val interceptors = interceptors()
            val index = interceptors.indexOfFirst { "Brotli" in it.javaClass.simpleName }
            if (index >= 0) {
                interceptors.add(interceptors.removeAt(index))
            }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        return searchMangaParse(response)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("word", query)
                .build()
            return GET(url, headers)
        }
        val filterSelect = filters.firstInstanceOrNull<AllFilter>()
        if (filterSelect != null && filterSelect.state != 0 && allFiltersList.isNotEmpty()) {
            val selection = allFiltersList[filterSelect.state]
            val urlBuilder = "$baseUrl/titles".toHttpUrl().newBuilder()
                .addQueryParameter(selection.key, selection.value)
            return GET(urlBuilder.build(), headers)
        }
        return GET("$baseUrl/titles", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.toString().contains("/search")) {
            val document = response.asJsoup()
            val mangas = document.select(".content-container .grid-group .w-auto a").map { link ->
                SManga.create().apply {
                    setUrlWithoutDomain(link.attr("href"))
                    val img = link.selectFirst("img")!!
                    title = img.attr("alt")
                    thumbnail_url = img.attr("abs:src")
                }
            }
            return MangasPage(mangas, false)
        }
        val document = response.asJsoup()

        if (allFiltersList.isEmpty()) {
            readAtTimestamp = document.selectFirst("#more_titles_button")?.attr("data-read-at")

            val filters = mutableListOf<FilterOption>()
            filters.add(FilterOption("All", "none", "none"))

            document.select("h3:contains(レーベルから選ぶ)").firstOrNull()
                ?.nextElementSibling()?.select("a")?.forEach {
                    filters.add(FilterOption(it.text(), "label", it.attr("href").substringAfter("=")))
                }

            document.select("h3:contains(ジャンルから選ぶ)").firstOrNull()
                ?.nextElementSibling()?.select("a")?.forEach {
                    filters.add(FilterOption(it.text(), "genre", it.attr("href").substringAfter("=")))
                }

            document.select("h3:contains(カテゴリから選ぶ)").firstOrNull()
                ?.nextElementSibling()?.select("a")?.forEach {
                    filters.add(FilterOption(it.text(), "category", it.attr("href").substringAfter("=")))
                }
            allFiltersList = filters
        }

        val mangaList = document.select("#titles-container a, .content-container .grid-group .w-auto a").map { link ->
            SManga.create().apply {
                setUrlWithoutDomain(link.attr("href"))
                val img = link.selectFirst("img")!!
                title = img.attr("alt")
                thumbnail_url = img.attr("abs:src")
            }
        }

        val readAtTimestamp = document.selectFirst("#more_titles_button")?.attr("data-read-at")
        if (readAtTimestamp != null) {
            val apiUrlBuilder = "$apiUrl/title-list".toHttpUrl().newBuilder()
                .addQueryParameter("read_at", readAtTimestamp)

            response.request.url.queryParameterNames.forEach { param ->
                if (param != "read_at") {
                    response.request.url.queryParameter(param)?.let { value ->
                        apiUrlBuilder.addQueryParameter(param, value)
                    }
                }
            }
            val apiRequest = GET(apiUrlBuilder.build(), headers)
            val apiResponse = client.newCall(apiRequest).execute()
            val apiResult = apiResponse.parseAs<ApiTitlesResponse>()

            val apiMangas = apiResult.data.map { apiTitle ->
                SManga.create().apply {
                    title = apiTitle.name
                    setUrlWithoutDomain(apiTitle.url)
                    thumbnail_url = apiTitle.thumbnail
                }
            }
            return MangasPage(mangaList + apiMangas, false)
        }
        return MangasPage(mangaList, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("main h2")!!.text()
            thumbnail_url = thumbnail_url
            author = document.select("a[href*=/authors/]").joinToString(", ") { it.text() }
            description = document.selectFirst("#plot + div")?.text()
            genre = document.select("div.pt-5 a.button-gray").joinToString(", ") { it.text() }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(".episodes-container .episode-item")
            .filterNot { it.text().contains("未公開話") }
            .mapNotNull { item ->
                item.selectFirst("a")?.let { link ->
                    SChapter.create().apply {
                        setUrlWithoutDomain(link.attr("href"))
                        name = item.selectFirst(".episode-item-left")!!.text().trim()
                    }
                }
            }

        if (chapters.isNotEmpty()) {
            return chapters
        }

        document.selectFirst(".header-side a.episode-read")?.let { oneshotLink ->
            val chapter = SChapter.create().apply {
                setUrlWithoutDomain(oneshotLink.attr("href"))
                name = document.selectFirst(".latest-episode-title")?.text()?.trim()!!
                val dateStr = document.selectFirst(".last-update")?.text()?.substringBefore("更新")
                date_upload = dateFormat.tryParse(dateStr)
            }
            return listOf(chapter)
        }
        return emptyList()
    }

    private val reader by lazy { SpeedBinbReader(client, headers, json) }

    override fun pageListParse(response: Response): List<Page> {
        return reader.pageListParse(response)
    }

    private class FilterOption(val name: String, val key: String, val value: String)
    private class AllFilter(options: Array<String>) : Filter.Select<String>("Filter by", options)

    override fun getFilterList(): FilterList {
        val filterList = if (allFiltersList.isEmpty()) {
            listOf(Filter.Header("Press 'Reset' to attempt to load filters"))
        } else {
            listOf(AllFilter(allFiltersList.map { it.name }.toTypedArray()))
        }
        return FilterList(filterList)
    }

    // Unsupported
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
