package eu.kanade.tachiyomi.multisrc.mangaverse

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

abstract class MangaVerse(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
) : HttpSource() {

    override val supportsLatest = false

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$lang/?s=&lang=$lang", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$lang/?s=&lang=$lang", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/$lang/?s=${java.net.URLEncoder.encode(query, "UTF-8")}&lang=$lang", headers)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".series-card").map { card ->
            val link = card.select(".series-card-link")
            val href = link.attr("href")
            val url = if (href.startsWith("http")) href.removePrefix(baseUrl) else href
            val title = card.select(".series-card-title").text()
            val thumbStyle = card.select(".series-card-thumb").attr("style")
            val thumbnail = Regex("""url\(['"]?(.*?)['"]?\)""").find(thumbStyle)?.groupValues?.get(1) ?: ""
            SManga.create().apply {
                this.url = url
                this.title = title
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            title = doc.select(".series-title").text()
            description = doc.select(".series-description").text()
            thumbnail_url = doc.select(".series-header-thumbnail img").attr("abs:src")
            genre = doc.select(".series-meta span").joinToString { it.text() }
            status = SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val categoryId = doc.select(".chapters-list").attr("data-category")

        val chapters = doc.select(".chapter-item").map { item ->
            val link = item.select(".chapter-link")
            val href = link.attr("href")
            val url = if (href.startsWith("http")) href.removePrefix(baseUrl) else href
            SChapter.create().apply {
                this.url = url
                name = item.select(".chapter-title").text()
                chapter_number = Regex("""(\d+(?:\.\d+)?)""").find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            }
        }

        val totalChapters = doc.select("#load-more-series").attr("data-total").toIntOrNull() ?: 0
        val allChapters = chapters.toMutableList()

        if (allChapters.size < totalChapters && categoryId.isNotBlank()) {
            val nonce = extractNonce(doc)
            if (nonce.isNotBlank()) {
                var page = 2
                while (allChapters.size < totalChapters && page <= 100) {
                    try {
                        val ajaxChapters = loadChaptersAjax(categoryId, nonce, page)
                        if (ajaxChapters.isEmpty()) break
                        allChapters.addAll(ajaxChapters)
                        page++
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        }

        return allChapters.distinctBy { it.chapter_number }.sortedByDescending { it.chapter_number }
    }

    private fun extractNonce(doc: org.jsoup.nodes.Document): String {
        val scripts = doc.select("script")
        for (script in scripts) {
            val text = script.html()
            if (text.contains("mangaverse_ajax")) {
                val match = Regex("nonce\":\"(.*?)\"").find(text)
                if (match != null) return match.groupValues[1]
            }
        }
        return ""
    }

    private fun loadChaptersAjax(categoryId: String, nonce: String, page: Int): List<SChapter> {
        val body = FormBody.Builder()
            .add("action", "mangaverse_load_more")
            .add("nonce", nonce)
            .add("page", page.toString())
            .add("type", "series")
            .add("category_id", categoryId)
            .add("order", "desc")
            .add("lang", lang)
            .build()

        val request = POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
        val response = client.newCall(request).execute()
        val json = response.body?.string() ?: return emptyList()

        val htmlMatch = Regex("\"html\":\"(.*?)\"").find(json) ?: return emptyList()
        val html = htmlMatch.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\/", "/")

        val parsedDoc = Jsoup.parseBodyFragment(html)
        return parsedDoc.select(".chapter-item").map { item ->
            val link = item.select(".chapter-link")
            val href = link.attr("href")
            val url = if (href.startsWith("http")) href.removePrefix(baseUrl) else href
            SChapter.create().apply {
                this.url = url
                name = item.select(".chapter-title").text()
                chapter_number = Regex("""(\d+(?:\.\d+)?)""").find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val pages = doc.select("figure.wp-block-image img").mapIndexed { index, img ->
            val dataSrc = img.attr("data-src")
            val src = img.attr("src")
            val url = if (!dataSrc.isNullOrBlank()) dataSrc else src.orEmpty()
            Page(index, imageUrl = url)
        }
        if (pages.isNotEmpty()) return pages

        return doc.select("noscript img").mapIndexed { index, img ->
            val url = img.attr("src").orEmpty()
            Page(index, imageUrl = url)
        }.filter { !it.imageUrl.isNullOrBlank() && !it.imageUrl!!.startsWith("data:") }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList = FilterList()
}
