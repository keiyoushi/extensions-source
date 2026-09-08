package eu.kanade.tachiyomi.extension.ja.rawxz

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.util.Calendar

@Source
abstract class RawXZ : KeiSource() {
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/manga/page/$page/?orderby=views").asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/manga/page/$page/?orderby=date").asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("")
            }
            addQueryParameter("s", query)
            addQueryParameter("post_type", "manga")
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".manga-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".manga-card-title")!!.text().removeSuffix(" (Raw – Free)")
                setUrlWithoutDomain(element.selectFirst("a.manga-card-thumb")!!.absUrl("href"))
                thumbnail_url = element.selectFirst(".manga-card-thumb img")?.absUrl("src")
            }
        }

        val hasNextPage = mangas.size >= 40 && document.selectFirst(".pagination a:has(i.fa-chevron-right)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseDetails(document), parseChapters(document))
    }

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".md-title")!!.text().removeSuffix(" (Raw – Free)")
        author = document.select(".md-meta-row:has(.fa-user) .md-meta-val").text().takeIf { it != "更新中" }
        status = parseStatus(document.selectFirst(".md-meta-row:has(.fa-rss) .md-meta-val")?.text())
        genre = document.select(".md-tag").joinToString { it.text() }
        description = document.selectFirst(".md-desc-content")?.text()
        thumbnail_url = document.selectFirst(".md-cover img")?.absUrl("src")
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("連載中") -> SManga.ONGOING
        status.contains("完結") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select(".md-chapter-row").map { element ->
        SChapter.create().apply {
            val link = element.selectFirst(".md-chapter-name a")!!
            name = link.ownText()
            setUrlWithoutDomain(link.absUrl("href"))
            date_upload = parseRelativeDate(element.selectFirst(".md-chapter-time")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("http")) {
            throw Exception("この章のURLは古くなっています。マンガを更新してください。")
        }

        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select(".reader-page img").mapIndexed { index, img ->
            Page(
                index = index,
                imageUrl = toProxyUrl(img.absUrl("src")),
            )
        }
    }

    private fun toProxyUrl(url: String): String {
        if (url.isBlank() || url.contains("img-proxy.php?url=")) return url

        return baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("wp-content/themes/manga-theme-MangaVerse/img-proxy.php")
            addQueryParameter("url", url)
        }.build().toString()
    }

    private fun parseRelativeDate(date: String?): Long {
        if (date == null) return 0L

        val calendar = Calendar.getInstance()
        val amount = date.split(" ")[0].filter { it.isDigit() }.toIntOrNull() ?: return 0L

        when {
            date.contains("秒前") -> calendar.add(Calendar.SECOND, -amount)
            date.contains("分前") -> calendar.add(Calendar.MINUTE, -amount)
            date.contains("時間前") -> calendar.add(Calendar.HOUR, -amount)
            date.contains("日前") -> calendar.add(Calendar.DATE, -amount)
            date.contains("週間前") -> calendar.add(Calendar.WEEK_OF_YEAR, -amount)
            date.contains("ヶ月前") -> calendar.add(Calendar.MONTH, -amount)
            date.contains("年前") -> calendar.add(Calendar.YEAR, -amount)
            else -> return 0L
        }

        return calendar.timeInMillis
    }
}
