package eu.kanade.tachiyomi.extension.ja.alphapolis

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale

class Alphapolis : HttpSource() {
    override val name = "Alphapolis"
    override val baseUrl = "https://www.alphapolis.co.jp"
    override val lang = "ja"
    override val supportsLatest = true

    // Load proper thumbnails
    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36")

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd'更新'", Locale.ROOT)

    private var xsrfToken: String? = null

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.headers("Set-Cookie").firstOrNull { it.startsWith("XSRF-TOKEN=") }?.let {
                xsrfToken = it.substringAfter("XSRF-TOKEN=").substringBefore(";").let { encoded ->
                    URLDecoder.decode(encoded, "UTF-8")
                }
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/official/ranking?category=total", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".official-manga-sub-like_ranking--list, .official-manga-sub-like_ranking--panel").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".official-manga-sub-like_ranking--list_title, .official-manga-sub-like_ranking--panel_title")!!.text()
                thumbnail_url = it.selectFirst("img, .official-manga-sub-like_ranking--panel_thumbnail")
                    ?.let { thumb -> thumb.absUrl("data-src").ifEmpty { thumb.absUrl("data-bg") } }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/official/search?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga/official/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("query", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is CategoryFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, option ->
                        url.addQueryParameter("category[$index]", option.value)
                    }
                }
                is LabelFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, option ->
                        url.addQueryParameter("label[$index]", option.value)
                    }
                }
                is StatusFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, option ->
                        url.addQueryParameter("complete[$index]", option.value)
                    }
                }
                is RentalFilter -> {
                    filter.state.filter { it.state }.forEachIndexed { index, option ->
                        url.addQueryParameter("rental[$index]", option.value)
                    }
                }
                is DailyFreeFilter -> {
                    if (filter.state) {
                        url.addQueryParameter("is_free_daily", "enable")
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mangas-list .official-manga-panel > a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst(".panel")?.absUrl("data-bg")
            }
        }
        val hasNextPage = document.selectFirst("i.fa.fa-angle-double-right") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".manga-detail-description > .title > h1")!!.text()
            val authors = document.select(".manga-detail-description .author-label .authors .mangaka").toList()
            author = authors.filter { it.text().contains("原作") }
                .mapNotNull { it.selectFirst("a")?.text() }
                .joinToString()
            artist = authors.filter { it.text().contains("漫画") }
                .mapNotNull { it.selectFirst("a")?.text() }
                .joinToString()
            description = document.selectFirst(".manga-detail-outline .outline")?.text()
            genre = document.select(".manga-detail-tags .official-manga-tags .official-manga-tag").joinToString { it.text() }
            status = when (document.selectFirst(".wrap-content-status a[href*=complete]")?.text()) {
                "連載中" -> SManga.ONGOING
                "完結" -> SManga.COMPLETED
                "休載中" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst(".manga-bigbanner img")?.absUrl("src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".episode-list .episode-unit").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a.read-episode")!!.absUrl("href"))
                val titleText = it.selectFirst(".title")!!.text()
                val isRental = it.selectFirst(".rental-coin") != null ||
                    it.selectFirst(".icon-zero-yen") != null
                if (isRental) name = "\uD83E\uDE99 $titleText" else name = titleText
                date_upload = dateFormat.tryParse(it.selectFirst(".up-time")?.text())
            }
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.fromCallable {
            val parts = (baseUrl + chapter.url).toHttpUrl().pathSegments
            val mangaId = parts[parts.size - 2].toInt()
            val episodeId = parts.last().toInt()

            val token = xsrfToken ?: getXsrfToken() ?: throw Exception("XSRF-Token not found")

            val viewerUrl = "$baseUrl/manga/official/viewer.json"

            val newHeaders = super.headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("X-XSRF-TOKEN", token)
                .set("Referer", getChapterUrl(chapter))
                .build()

            fun getPages(resolution: String): List<Page> {
                val body =
                    ViewerRequestBody(episodeId, false, mangaId, false, resolution).toJsonString()
                        .toRequestBody("application/json".toMediaType())
                val request = POST(viewerUrl, newHeaders, body)

                return client.newCall(request).execute().use {
                    if (!it.isSuccessful) return emptyList()
                    it.parseAs<ViewerResponse>().page?.images?.mapIndexed { i, img ->
                        Page(i, imageUrl = img.url)
                    } ?: throw Exception("Log in via WebView and purchase this chapter to read.")
                }
            }

            val resolutions = listOf("full_hd", "standard")
            val pages = resolutions.asSequence().map { getPages(it) }
                .firstOrNull { it.isNotEmpty() }
                ?: throw Exception("Log in via WebView and purchase this chapter to read.")

            pages
        }
    }

    private fun getXsrfToken(): String? {
        val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        return cookies.firstOrNull { it.name == "XSRF-TOKEN" }?.value?.let {
            URLDecoder.decode(it, "UTF-8")
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Filters will be applied to search query"),
        CategoryFilter(),
        LabelFilter(),
        StatusFilter(),
        RentalFilter(),
        DailyFreeFilter(),
    )

    private class CategoryFilter : Filter.Group<FilterTag>(
        "カテゴリ",
        listOf(
            FilterTag("男性向け", "men"),
            FilterTag("女性向け", "women"),
            FilterTag("TL", "tl"),
            FilterTag("BL", "bl"),
        ),
    )

    private class LabelFilter : Filter.Group<FilterTag>(
        "レーベル",
        listOf(
            FilterTag("アルファポリス", "alphapolis"),
            FilterTag("レジーナ", "regina"),
            FilterTag("アルファノルン", "alphanorn"),
            FilterTag("エタニティ", "eternity"),
            FilterTag("ノーチェ", "noche"),
            FilterTag("アンダルシュ", "andarche"),
        ),
    )

    private class StatusFilter : Filter.Group<FilterTag>(
        "進行状況",
        listOf(
            FilterTag("連載中", "running"),
            FilterTag("完結", "finished"),
            FilterTag("休載中", "sleeping"),
        ),
    )

    private class RentalFilter : Filter.Group<FilterTag>(
        "レンタル",
        listOf(
            FilterTag("レンタルあり", "enable"),
            FilterTag("全話無料", "disable"),
        ),
    )

    private class DailyFreeFilter : Filter.CheckBox("毎日¥0")

    private class FilterTag(name: String, val value: String) : Filter.CheckBox(name)
}
