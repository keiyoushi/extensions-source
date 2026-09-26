package eu.kanade.tachiyomi.multisrc.mmlook

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

// Rumanhua legacy preference:
// const val APP_CUSTOMIZATION_URL = "APP_CUSTOMIZATION_URL"

/** 漫漫看 */
abstract class MMLook : KeiSource() {

    protected open val desktopUrl get() = baseUrl.replace("https://m.", "https://www.")

    protected open val useLegacyMangaUrl: Boolean = false

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = followRedirects(false)
        .hostnameVerifier { _, _ -> true }

    private fun String.certificateWorkaround() = replace("https:", "http:")

    private fun SManga.formatUrl() = apply { if (useLegacyMangaUrl) url = "/$url/" }

    private suspend fun fetchRanking(id: String) = parseRanking(client.get("$desktopUrl/rank/$id").asJsoup())

    override suspend fun getPopularManga(page: Int) = fetchRanking("1")

    private fun parseRanking(document: Document): MangasPage {
        val entries = document.select(".likedata").map { element ->
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

    override suspend fun getLatestUpdates(page: Int) = fetchRanking("5")

    override fun getFilterList(data: JsonElement?) = FilterList(
        RankingFilter(),
        Filter.Separator(),
        Filter.Header("分类（搜索文本、查看排行榜时无效）"),
        CategoryFilter(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val document = client.post(
                "$desktopUrl/s",
                FormBody.Builder().add("k", query.take(12)).build(),
            ).asJsoup()
            return parseSearch(document)
        }
        for (filter in filters) {
            when (filter) {
                is RankingFilter -> if (filter.state > 0) {
                    return fetchRanking(filter.options[filter.state].value)
                }

                is CategoryFilter -> if (filter.state > 0) {
                    val id = filter.options[filter.state].value
                    return parseRanking(client.get("$desktopUrl/sort/$id").asJsoup())
                }

                else -> {}
            }
        }
        return getPopularManga(page)
    }

    private fun parseSearch(document: Document): MangasPage {
        val entries = document.select(".item-data > div").map { element ->
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
    // "more chapter" request must be sent to the same domain
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.url.removeSurrounding("/")
        val document = client.get("$desktopUrl/$mangaId/").asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), fetchChapterList(document, mangaId))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val comicInfo = document.selectFirst(".comicInfo")!!
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

    private suspend fun fetchChapterList(document: Document, mangaId: String): List<SChapter> {
        val container = document.selectFirst(".chapterlistload")!!
        val chapters = container.child(0).children().mapTo(ArrayList()) { element ->
            SChapter.create().apply {
                url = element.attr("href").mustRemoveSurrounding("/", ".html")
                name = element.text()
            }
        }
        if (container.selectFirst(".chaplist-more") != null) {
            client.post(
                "$desktopUrl/morechapter",
                FormBody.Builder().addEncoded("id", mangaId).build(),
            ).parseAs<ResponseDto>().data
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

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.fullUrl()).asJsoup()
        val id = document.selectFirst(".readerContainer")!!.attr("data-id").toInt()
        return document.selectFirst("script:containsData(eval)")!!.data()
            .let(Unpacker::unpack)
            .mustRemoveSurrounding("var __c0rst96=\"", "\"")
            .let { decrypt(it, id) }
            .parseAs<List<String>>()
            .mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }
}

private fun String.mustRemoveSurrounding(prefix: String, suffix: String): String {
    check(startsWith(prefix) && endsWith(suffix)) { "string doesn't match $prefix[...]$suffix" }
    return substring(prefix.length, length - suffix.length)
}
