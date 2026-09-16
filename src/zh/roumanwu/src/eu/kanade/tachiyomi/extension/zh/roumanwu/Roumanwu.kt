package eu.kanade.tachiyomi.extension.zh.roumanwu

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Roumanwu : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambledImageInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/books".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .build()
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/home").asJsoup()
        return parseHomePage(document, Regex("最近更新"))
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("page", (page - 1).toString())
                .build()
        } else {
            "$baseUrl/books".toHttpUrl().newBuilder()
                .addQueryParameter("page", (page - 1).toString())
                .apply {
                    when (filters.firstInstance<StatusFilter>().state) {
                        1 -> addQueryParameter("continued", "true")
                        2 -> addQueryParameter("continued", "false")
                    }
                }
                .build()
        }
        return parseMangaList(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() != "books") return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return parseMangaDetails(client.get("$baseUrl/books/$id").asJsoup())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val body = client.get(getChapterUrl(chapter)).use { it.body.string() }

        val fromNextJs = body.asJsoup(baseUrl).extractNextJs<ChapterPages>()?.toPageList().orEmpty()
        if (fromNextJs.isNotEmpty()) return fromNextJs

        // TanStack hydration: imagePaths:$R[n]=["https://...", ...]
        val marker = body.indexOf("imagePaths:")
        val arrayStart = if (marker >= 0) body.indexOf("=[", marker) + 1 else -1
        val arrayEnd = if (arrayStart > 0) body.indexOf(']', arrayStart) else -1
        if (arrayEnd < 0) return emptyList()
        return body.substring(arrayStart, arrayEnd + 1).parseAs<List<String>>()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("提示：搜尋時篩選無效"),
        StatusFilter(),
    )

    private fun parseEntries(container: Element): List<SManga> = container.select("a.site-comic").map {
        SManga.create().apply {
            title = it.selectFirst("h3")!!.text()
            url = it.attr("href")
            thumbnail_url = it.selectFirst("img")!!.absUrl("src")
        }
    }

    private fun parseMangaList(document: Document): MangasPage = MangasPage(parseEntries(document), hasNextPage(document))

    // 页码文案形如 "1 / 103"；末页「下一頁」会变成 disabled button
    private fun hasNextPage(document: Document): Boolean {
        val parts = document.selectFirst(".site-pagination-mobile")?.text()?.split('/').orEmpty()
        val current = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return false
        val total = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return false
        return current < total
    }

    private fun parseHomePage(document: Document, sections: Regex): MangasPage {
        val entries = document.selectFirst("div.site-home")!!.children().flatMap { section ->
            val heading = section.selectFirst(".site-section-heading")?.text().orEmpty()
            if (heading.contains(sections)) {
                parseEntries(section)
            } else {
                emptyList()
            }
        }.distinctBy { it.url }

        return MangasPage(entries, false)
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        val info = document.selectFirst("div.site-book-info")!!

        setUrlWithoutDomain(document.location())
        title = info.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst("img.site-detail-cover")!!.absUrl("src")

        val alias = info.selectFirst("p.site-book-alias")?.text()
        val synopsis = document.selectFirst("div.site-book-synopsis")?.text().orEmpty()
        description = buildString {
            if (!alias.isNullOrEmpty() && alias != title) {
                append("別名: ")
                append(alias)
                append("\n\n")
            }
            append(synopsis)
        }

        val data = info.select("dl.site-book-data dt").associate { dt ->
            dt.text() to dt.nextElementSibling()?.text().orEmpty()
        }
        author = data["作者"]
        status = when {
            data["狀態"]?.startsWith("連載中") == true -> SManga.ONGOING
            data["狀態"]?.startsWith("已完結") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val genres = ArrayList<String>()
        data["地區"]?.takeIf { it.isNotEmpty() }?.let { genres.add(it) }
        info.selectFirst("p.site-eyebrow")?.text()
            ?.substringBefore("/")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { genres.add(it) }
        genre = genres.joinToString()
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapters = document.select("a.site-chapter-link").map {
            SChapter.create().apply {
                url = it.attr("href")
                name = it.selectFirst("span")!!.attr("title")
            }
        }.asReversed()
        if (chapters.isNotEmpty()) {
            val date = DATE_FORMAT.tryParseDate(
                document.selectFirst("dl.site-book-data dt:contains(更新) + dd")?.text(),
                ZoneId.of("Asia/Taipei"),
            )
            if (date != 0L) {
                chapters[0].date_upload = date
            }
        }
        return chapters
    }

    private class StatusFilter : Filter.Select<String>("狀態", arrayOf("全部", "連載中", "已完結"))

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy")
    }
}
