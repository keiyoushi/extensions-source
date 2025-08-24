package eu.kanade.tachiyomi.multisrc.mmlook

import eu.kanade.tachiyomi.lib.unpacker.Unpacker
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
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response

// Rumanhua legacy preference:
// const val APP_CUSTOMIZATION_URL = "APP_CUSTOMIZATION_URL"

/** 漫漫看 */
open class MMLook(
    override val name: String,
    override val baseUrl: String,
    private val desktopUrl: String,
    private val useLegacyMangaUrl: Boolean,
) : HttpSource() {
    override val lang: String get() = "zh"
    override val supportsLatest: Boolean get() = true

    override val client = network.cloudflareClient.newBuilder()
        .followRedirects(false)
        .hostnameVerifier { _, _ -> true }
        .build()

    private fun String.certificateWorkaround() = replace("https:", "http:")

    private fun SManga.formatUrl() = apply { if (useLegacyMangaUrl) url = "/$url/" }

    private fun rankingRequest(id: String) = GET("$desktopUrl/rank/$id", headers)

    override fun popularMangaRequest(page: Int) = rankingRequest("1")

    override fun popularMangaParse(response: Response): MangasPage {
        val entries = response.asJsoup().select(".likedata").map { element ->
            SManga.create().apply {
                url = element.select("a").attr("href").mustRemoveSurrounding("/", "/")
                title = element.selectFirst(".le-t")!!.text()
                author = element.selectFirst(".likeinfo > p")!!.text()
                    .mustRemoveSurrounding("作者：", "")
                description = element.selectFirst(".le-j")!!.text()
                thumbnail_url = element.selectFirst("img")!!.attr("data-src")
            }.formatUrl()
        }
        return MangasPage(entries, false)
    }

    override fun latestUpdatesRequest(page: Int) = rankingRequest("5")

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        RankingFilter(),
        Filter.Separator(),
        Filter.Header("分类（搜索文本、查看排行榜时无效）"),
        CategoryFilter(),
    )

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return POST(
                "$desktopUrl/s",
                headers,
                FormBody.Builder().add("k", query.take(12)).build(),
            )
        }
        for (filter in filters) {
            when (filter) {
                is RankingFilter -> if (filter.state > 0) {
                    return rankingRequest(filter.options[filter.state].value)
                }

                is CategoryFilter -> if (filter.state > 0) {
                    val id = filter.options[filter.state].value
                    return GET("$desktopUrl/sort/$id", headers)
                }

                else -> {}
            }
        }
        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.method == "GET") return popularMangaParse(response)

        val entries = response.asJsoup().select(".item-data > div").map { element ->
            SManga.create().apply {
                url = element.selectFirst("a")!!.attr("href").mustRemoveSurrounding("/", "/")
                title = element.selectFirst(".e-title, .title")!!.text()
                author = element.selectFirst(".tip")!!.text()
                thumbnail_url = element.selectFirst("img")!!.attr("data-src")
            }.formatUrl()
        }
        return MangasPage(entries, false)
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.removeSurrounding("/")
        return "$baseUrl/$id/".certificateWorkaround()
    }

    // Desktop page has consistent template and more initial chapters
    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.removeSurrounding("/")
        return GET("$desktopUrl/$id/", headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val comicInfo = response.asJsoup().selectFirst(".comicInfo")!!
        thumbnail_url = comicInfo.selectFirst("img")!!.attr("data-src")

        val container = comicInfo.selectFirst(".detinfo")!!
        title = container.selectFirst("h1")!!.text()

        var updated = ""
        for (span in container.select("span")) {
            val text = span.ownText()
            val value = text.substring(4).trimStart()
            when (val key = text.substring(0, 4)) {
                "作 者：" -> author = value
                "更新时间" -> updated = "$text\n\n"
                "标 签：" -> genre = value.replace(" ", ", ")
                "状 态：" -> status = when (value) {
                    "连载中" -> SManga.ONGOING
                    "已完结" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }

                else -> throw Exception("Unknown field: $key")
            }
        }

        description = updated + container.selectFirst(".content")!!.text()
    }

    // Desktop page contains more initial chapters
    // "more chapter" request must be sent to the same domain
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val container = response.asJsoup().selectFirst(".chapterlistload")!!
        val chapters = container.child(0).children().mapTo(ArrayList()) { element ->
            SChapter.create().apply {
                url = element.attr("href").mustRemoveSurrounding("/", ".html")
                name = element.text()
            }
        }
        if (container.selectFirst(".chaplist-more") != null) {
            val mangaId = response.request.url.pathSegments[0]
            val request = POST(
                "$desktopUrl/morechapter",
                headers,
                FormBody.Builder().addEncoded("id", mangaId).build(),
            )
            client.newCall(request).execute().parseAs<ResponseDto>().data
                .mapTo(chapters) { it.toSChapter(mangaId) }
        }
        return chapters
    }

    private fun SChapter.fullUrl(): String {
        val url = this.url
        if (url.startsWith('/')) throw Exception("请刷新章节列表")
        return "$baseUrl/$url.html"
    }

    override fun getChapterUrl(chapter: SChapter) = chapter.fullUrl().certificateWorkaround()

    override fun pageListRequest(chapter: SChapter): Request = GET(chapter.fullUrl(), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val id = document.selectFirst(".readerContainer")!!.attr("data-id").toInt()
        return document.selectFirst("script:containsData(eval)")!!.data()
            .let(Unpacker::unpack)
            .mustRemoveSurrounding("var __c0rst96=\"", "\"")
            .let { decrypt(it, id) }
            .parseAs<List<String>>()
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()
}

private fun String.mustRemoveSurrounding(prefix: String, suffix: String): String {
    check(startsWith(prefix) && endsWith(suffix)) { "string doesn't match $prefix[...]$suffix" }
    return substring(prefix.length, length - suffix.length)
}
