package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class GocTruyenTranhVui() : HttpSource() {
    override val lang = "vi"

    override val baseUrl = "https://goctruyentranhvui17.com"

    override val name = "Goc Truyen Tranh Vui"

    private val apiUrl = "$baseUrl/api/v2"

    private val searchUrl = "$baseUrl/api/comic/search"

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val urls = client.newCall(GET(baseUrl + manga.url, headers)).execute().asJsoup()
        val mangaId = checkChapterLists(urls)
        return client.newCall(GET("$baseUrl/api/comic/$mangaId/chapter?offset=21&limit=-1", headers))
            .asObservableSuccess()
            .map { response ->
                val res = response.parseAs<ChapterDTO>()
                res.result.chapters.map { itm ->
                    SChapter.create().apply {
                        name = itm.numberChapter
                        date_upload = dateFormat.tryParse(itm.stringUpdateTime)
                        setUrlWithoutDomain("$baseUrl${manga.url}/chuong-${itm.numberChapter}")
                    }
                }
            }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    private fun checkChapterLists(document: Document) = document.selectFirst("input[id=comic-id-comment]")!!.attr("value")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val element = response.asJsoup()
        val manga = element.select(".row .col-md-2").map { itm ->
            SManga.create().apply {
                setUrlWithoutDomain(itm.select("a.mt-1").attr("href"))
                title = itm.select("a.mt-1").text()
                thumbnail_url = itm.selectFirst("img.lazy")!!.absUrl("data-original")
            }
        }
        val hasNextPage = element.select(".ma-3 a").text().isNotEmpty()
        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("truyen-cap-nhat")
            addQueryParameter("p", page.toString())
        }.build(),
        headers,
    )

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text().trim(',', ' ') }
        thumbnail_url = document.selectFirst("img.image")!!.absUrl("src")
        status = parseStatus(document.select(".mb-1:contains(Trạng thái:) span").text())
        author = document.select(".mb-1:contains(Tác giả:) span").text()
        description = document.select(".v-card-text").text()
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang thực hiện", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val pattern = Regex("chapterJson:\\s*`(.*?)`", RegexOption.DOT_MATCHES_ALL)
        val match = pattern.find(html)
        val jsonString = match?.groups?.get(1)?.value ?: error("Không tìm thấy chapterJson")
        val result = jsonString.parseAs<ChapterWrapper>()
        val imageList = result.body.result.data
        return imageList.mapIndexed { i, url ->
            val finalUrl = if (url.startsWith("/image/")) {
                "$baseUrl$url"
            } else {
                url
            }
            Page(i, imageUrl = finalUrl)
        }
    }
    override fun imageUrlParse(response: Response): String = ""

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("home/filter")
            addQueryParameter("p", page.toString())
            addQueryParameter("value", "recommend")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val json = response.parseAs<PopularDTO>()
        val manga = json.result.data.map {
            SManga.create().apply {
                title = it.name
                thumbnail_url = baseUrl + it.photo
                setUrlWithoutDomain("$baseUrl/truyen/" + it.nameEn)
            }
        }
        val hasNextPage = json.result.p != 100
        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = searchUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query)
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val res = response.parseAs<SearchDTO>()
        val manga = res.result.map { item ->
            SManga.create().apply {
                title = item.name
                thumbnail_url = baseUrl + item.photo
                setUrlWithoutDomain("$baseUrl/truyen/" + item.nameEn)
            }
        }
        val hasNextPage = false
        return MangasPage(manga, hasNextPage)
    }
}
