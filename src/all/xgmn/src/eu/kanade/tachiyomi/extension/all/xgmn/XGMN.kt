package eu.kanade.tachiyomi.extension.all.xgmn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class XGMN : HttpSource() {
    override val baseUrl get() = redirectUrl ?: "http://xgmn8.vip"
    override val lang = "all"
    override val name = "性感美女"
    override val supportsLatest = true
    private var redirectUrl: String? = null

    companion object {
        val ID_REGEX = Regex("\\d+(?=\\.html)")
        val PAGE_SIZE_REGEX = Regex("\\d+(?=P)")
        val DATE_FORMAT = SimpleDateFormat("yyyy.MM.dd", Locale.CHINA)
    }

    private fun getUrlWithoutDomain(url: String): String {
        val prefix = listOf("http://", "https://").firstOrNull(url::startsWith)
        return url.substringAfter(prefix ?: "").substringAfter('/')
    }

    // Popular Page

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/top.html", headers)

    override fun popularMangaParse(response: Response) = response.asJsoup().let { doc ->
        redirectUrl = redirectUrl ?: doc.location().toHttpUrl().let { "${it.scheme}://${it.host}" }
        val cur = doc.selectFirst(".current")?.text()?.toInt()
        MangasPage(
            doc.select(".related_box").map {
                SManga.create().apply {
                    thumbnail_url = it.selectFirst("img")?.absUrl("src")
                    it.selectFirst("a")!!.let {
                        title = it.attr("title")
                        setUrlWithoutDomain(it.absUrl("href"))
                    }
                }
            },
            cur != null && cur < doc.selectFirst(".pagination strong")!!.text().toInt(),
        )
    }

    // Latest Page

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/new.html", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search Page

    override fun getFilterList() = buildFilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegments("/plus/search/index.asp")
                .addQueryParameter("keyword", query)
                .addQueryParameter("p", page.toString())
        } else {
            url.addPathSegments(filters.first().toString())
            if (page > 1) url.addPathSegment("page_$page.html")
        }
        return GET(url.build())
    }

    override fun searchMangaParse(response: Response) = if (response.request.url.pathSegments.contains("search")) {
        val doc = response.asJsoup()
        redirectUrl = redirectUrl ?: doc.location().toHttpUrl().let { "${it.scheme}://${it.host}" }
        val current = doc.selectFirst(".current")!!.text().toInt()
        MangasPage(
            doc.select(".node > p > a").map {
                SManga.create().apply {
                    title = it.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                    thumbnail_url = "$baseUrl/uploadfile/pic/${ID_REGEX.find(url)?.value}.jpg"
                }
            },
            current < doc.select(".list .pagination a").size,
        )
    } else {
        popularMangaParse(response)
    }

    // Manga Detail Page

    override fun mangaDetailsParse(response: Response) = response.asJsoup().let { doc ->
        redirectUrl = redirectUrl ?: doc.location().toHttpUrl().let { "${it.scheme}://${it.host}" }
        SManga.create().apply {
            author = doc.selectFirst(".item-2")?.text()?.substringAfter("模特：")
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            status = SManga.COMPLETED
        }
    }

    // Manga Detail Page / Chapters Page (Separate)

    override fun chapterListParse(response: Response) = response.asJsoup().let { doc ->
        redirectUrl = redirectUrl ?: doc.location().toHttpUrl().let { "${it.scheme}://${it.host}" }
        listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(doc.selectFirst(".current")!!.absUrl("href"))
                name = doc.selectFirst(".article-title")!!.text()
                chapter_number = 1F
                date_upload = DATE_FORMAT.tryParse(
                    doc.selectFirst(".item-1")?.text()?.substringAfter("更新："),
                )
            },
        )
    }

    // Manga View Page

    override fun pageListParse(response: Response) = response.asJsoup().let { doc ->
        val prefix = doc.selectFirst(".current")!!.absUrl("href").substringBeforeLast(".html")
        val total = PAGE_SIZE_REGEX.find(doc.selectFirst(".article-title")!!.text())!!.value
        val size = doc.select(".article-content > p[style] > img").size
        List(total.toInt()) {
            Page(
                it,
                prefix + (it / size).let { v -> if (v == 0) "" else "_$v" } + ".html#${it % size + 1}",
            )
        }
    }

    // Image

    override fun imageUrlParse(response: Response): String {
        val seq = response.request.url.fragment!!
        val url = response.asJsoup()
            .selectXpath("//*[contains(@class,'article-content')]/p[@*[contains(.,'center')]]/img[position()=$seq]")
            .first() ?: throw Exception("没找到图片")
        return "$baseUrl/${getUrlWithoutDomain(url.absUrl("src"))}"
    }
}
