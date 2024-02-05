package eu.kanade.tachiyomi.extension.all.xinmeitulu

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Locale

class Xinmeitulu : ParsedHttpSource() {
    override val baseUrl = "https://www.xinmeitulu.com"
    override val lang = "all"
    override val name = "Xinmeitulu"
    override val supportsLatest = false

    override val client = network.client.newBuilder().addInterceptor(::contentTypeIntercept).build()

    // Latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesSelector() = throw UnsupportedOperationException()
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page")
    override fun popularMangaNextPageSelector() = ".next"
    override fun popularMangaSelector() = ".container > .row > div:has(figure)"
    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("figure > a").attr("abs:href"))
        title = element.select("figcaption").text()
        thumbnail_url = element.select("img").attr("abs:data-original-")
        genre = element.select("a.tag").joinToString(", ") { it.text() }
    }

    // Search

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.let { if (it.isEmpty()) getFilterList() else it }

        val url = baseUrl.toHttpUrl().newBuilder()

        if (!filterList.isEmpty()) {
            filterList.forEach { filter ->
                when (filter) {
                    is RegionFilter -> filter.toUriPart()?.let {
                        url
                            .addPathSegment("area")
                            .addPathSegment(it)
                    }
                    else -> {}
                }
            }
        }

        url.addPathSegment("page").addPathSegment(page.toString())
        url.addQueryParameter("s", query)

        return GET(url.toString(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith("SLUG:")) {
            val slug = query.removePrefix("SLUG:")
            client.newCall(GET("$baseUrl/photo/$slug", headers)).asObservableSuccess()
                .map { response -> MangasPage(listOf(mangaDetailsParse(response.asJsoup())), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    // Details

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        setUrlWithoutDomain(document.selectFirst("link[rel=canonical]")!!.attr("abs:href"))
        title = document.select(".container > h1").text()
        description = document.select(".container > *:not(div)").text()
        status = SManga.COMPLETED
        thumbnail_url = document.selectFirst("figure img")!!.attr("abs:data-original")
    }

    // Chapters

    override fun chapterListSelector() = "html"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("link[rel=canonical]")!!.attr("abs:href"))
        name = element.select(".container > h1").text()
    }

    override fun pageListParse(document: Document) =
        document.select(".container > div > figure img").mapIndexed { index, element ->
            Page(index, imageUrl = element.attr("abs:data-original"))
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList {
        return FilterList(
            RegionFilter(getRegionList()),
        )
    }

    private class RegionFilter(vals: Array<Pair<String?, String>>) : UriPartFilter("Region", vals)

    private fun getRegionList(): Array<Pair<String?, String>> {
        return if (Locale.getDefault().equals("zh")) {
            arrayOf(
                null to "全部",
                "zhongguodalumeinyu" to "中国大陆美女",
                "taiguomeinyu" to "泰国美女",
                "ribenmeinyu" to "日本美女",
                "hanguomeinyu" to "韩国美女",
                "taiwanmeinyu" to "台湾美女",
                "oumeimeinyu" to "欧美美女",
            )
        } else {
            arrayOf(
                null to "All",
                "zhongguodalumeinyu" to "Chinese beauty",
                "taiguomeinyu" to "Thailand beauty",
                "ribenmeinyu" to "Japanese beauty",
                "hanguomeinyu" to "Korean beauty",
                "taiwanmeinyu" to "Taiwanese beauty",
                "oumeimeinyu" to "European & American beauty",
            )
        }
    }

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String?, String>>) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray()) {
        fun toUriPart() = vals[state].first
    }

    companion object {
        private fun contentTypeIntercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.header("content-type")?.startsWith("image") == true) {
                val body = response.body.source().asResponseBody(jpegMediaType)
                return response.newBuilder().body(body).build()
            }
            return response
        }

        private val jpegMediaType = "image/jpeg".toMediaType()
    }
}
