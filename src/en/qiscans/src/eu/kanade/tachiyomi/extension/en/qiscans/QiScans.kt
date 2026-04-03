package eu.kanade.tachiyomi.extension.en.qiscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

class QiScans : HttpSource() {
    override val name = "QiScans"
    override val lang = "en"
    override val baseUrl = "https://qimanhwa.com"
    val apiUrl: String = "https://api.qimanhwa.com/api/v1"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Accept", "application/json, text/plain, */*")
        add("Origin", "https://qimanhwa.com")
        add("Referer", "https://qimanhwa.com/")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-site")
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

    override fun chapterListRequest(manga: SManga): Request {
        val url = "$baseUrl/series/${manga.url}"
        return GET(url, headers)
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val slug = response.request.url.pathSegments.last()
        var chapterPage = 1
        val cList = mutableListOf<Chapter>()
        do {
            val chapterUrl = "$apiUrl/series/$slug/chapters".toHttpUrl().newBuilder().apply {
                addQueryParameter("page", chapterPage.toString())
                addQueryParameter("perPage", "30")
                addQueryParameter("sort", "desc")
            }.build()
            var res = client.newCall(GET(chapterUrl, headers)).execute().parseAs<ChapterList>()
            cList.addAll(res.data)
            chapterPage++
        } while (res.nextPage != null)
        return cList.toList().map { it.toSChapter(slug) }
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            addQueryParameter("sort", "latest")
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val res = response.parseAs<SearchResponse>()
        var page = res.currentPage
        var hasNext = !res.nextPage!!.equals(null)
        val entries = res.data.filterNot { it.type == "NOVEL" }.map { it.toSManga() }
        return MangasPage(entries, hasNext)
    }

    // =============================== Details ===============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/series/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Manga>().toSManga()

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/${chapter.url}")

    override fun pageListParse(response: Response): List<Page> {
        val pages = response.parseAs<Images>()
        return pages.images.mapIndexed { index, dto -> Page(index, imageUrl = dto.url) }
    }

    // =============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", "20")
            addQueryParameter("sort", "popular")
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<SearchResponse>()
        var page = res.currentPage
        var hasNext = !res.nextPage!!.equals(null)
        val entries = res.data.filterNot { it.type == "NOVEL" }.map { it.toSManga() }
        return MangasPage(entries, hasNext)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        TODO("Not yet implemented")
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        TODO("Not yet implemented")
    }
}
