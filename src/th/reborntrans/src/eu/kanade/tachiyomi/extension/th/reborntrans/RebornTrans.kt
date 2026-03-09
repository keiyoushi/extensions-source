package eu.kanade.tachiyomi.extension.th.reborntrans

import eu.kanade.tachiyomi.extension.th.reborntrans.model.AjaxResponse
import eu.kanade.tachiyomi.extension.th.reborntrans.model.WpPost
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

/*
Author: github.com/keegang6705, Claude Sonnet 4.6, GPT-5.2
 */

class RebornTrans : HttpSource() {
    override val name = "Reborn Trans"
    override val baseUrl = "https://reborntrans.com"
    override val lang = "th"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().rateLimit(3).build()

    override fun headersBuilder() = super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US)

    private val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaAjax(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$ajaxUrl?action=manga_eagle_load_paginated_posts&page=$page&limit=15", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaAjax(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url =
            "$baseUrl/wp-json/wp/v2/posts"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("search", query)
                .addQueryParameter("_embed", "wp:featuredmedia")
                .addQueryParameter("per_page", "20")
                .addQueryParameter("page", page.toString())
                .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val posts = response.parseAs<List<WpPost>>()
        val totalPages = response.headers["X-WP-TotalPages"]?.toIntOrNull() ?: 1
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val mangas =
            posts.map { post ->
                SManga.create().apply {
                    title =
                        android.text.Html.fromHtml(
                            post.title.rendered,
                            android.text.Html.FROM_HTML_MODE_LEGACY,
                        )
                            .toString()
                            .trim()
                    setUrlWithoutDomain(post.link.removePrefix(baseUrl))
                    thumbnail_url = post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
                }
            }
        return MangasPage(mangas, currentPage < totalPages)
    }

    // ============================== Parsing ===============================

    private fun parseMangaAjax(response: Response): MangasPage {
        val ajax = response.parseAs<AjaxResponse>()
        val html = ajax.data?.html.orEmpty()
        val doc = org.jsoup.Jsoup.parseBodyFragment(html, baseUrl)
        val mangas =
            doc.select("a.manga-card").map { el ->
                SManga.create().apply {
                    title = el.selectFirst("h3")!!.text().trim()
                    setUrlWithoutDomain(el.attr("abs:href"))
                    thumbnail_url = el.selectFirst("img")?.absUrl("src")
                }
            }
        return MangasPage(mangas, mangas.size == 15)
    }

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.removeSuffix("/").substringAfterLast("/")
        val url =
            "$baseUrl/wp-json/wp/v2/posts"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("slug", slug)
                .addQueryParameter("_embed", "wp:featuredmedia,wp:term")
                .build()
        return GET(url, headers)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga {
        val post = response.parseAs<List<WpPost>>().first()
        return SManga.create().apply {
            title =
                android.text.Html.fromHtml(
                    post.title.rendered,
                    android.text.Html.FROM_HTML_MODE_LEGACY,
                )
                    .toString()
                    .trim()
            description = org.jsoup.Jsoup.parseBodyFragment(post.content.rendered).text().trim()
            thumbnail_url =
                post.meta?.coverUrl ?: post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
            genre =
                post.classList
                    .filter { it.startsWith("category-") && it != "category-uncategorized" }
                    .map {
                        it.removePrefix("category-").replace("-", " ").replaceFirstChar { c ->
                            c.uppercase()
                        }
                    }
                    .joinToString()
                    .ifEmpty { null }
            status =
                when (post.meta?.workStatus) {
                    "completed" -> SManga.COMPLETED
                    "processing", "ongoing" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}?tab=episodes", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("a.manga-single-episode-item").map { el ->
            SChapter.create().apply {
                setUrlWithoutDomain(el.attr("href"))
                name =
                    el.selectFirst("h3.manga-single-episode-item__title")?.text()?.trim()
                        ?: "ตอนที่ ${el.attr("data-episode-number")}"
                chapter_number = el.attr("data-episode-number").toFloatOrNull() ?: -1f
                date_upload =
                    el.selectFirst(".manga-single-episode-item__date")?.text()?.let {
                        parseDate(it)
                    }
                        ?: 0L
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.entry-content img, div#readerarea img, .reading-content img")
            .mapIndexed { i, img ->
                val url = img.absUrl("src").ifEmpty { img.absUrl("data-src") }
                Page(i, imageUrl = url)
            }
            .filterNot { it.imageUrl.isNullOrEmpty() || it.imageUrl!!.startsWith("data:") }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    // ============================== Helpers ===============================

    private fun parseDate(text: String): Long = runCatching { dateFormat.parse(text)?.time }.getOrNull() ?: 0L
}
