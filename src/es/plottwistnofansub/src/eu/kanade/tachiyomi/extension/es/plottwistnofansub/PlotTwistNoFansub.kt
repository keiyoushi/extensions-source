package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class PlotTwistNoFansub : HttpSource() {

    override val name = "Plot Twist No Fansub"

    override val baseUrl = "https://plotnofansub.com"

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("biblioteca")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("m_orderby", "trending")
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.page-listing-item figure").map { element ->
            SManga.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("href"))
                title = a.attr("title").takeIf { it.isNotEmpty() }
                    ?: element.selectFirst("figcaption")?.text()
                    ?: throw Exception("Missing title for manga at ${a.attr("href")}")
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers, a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("biblioteca")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("m_orderby", "latest3")
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            if (page > 1) {
                url.addPathSegment("page")
                url.addPathSegment(page.toString())
            }
            url.addQueryParameter("s", query)
            url.addQueryParameter("post_type", "wp-manga")
        } else {
            url.addPathSegment("biblioteca")
            if (page > 1) {
                url.addPathSegment("page")
                url.addPathSegment(page.toString())
            }
            url.addQueryParameter("m_orderby", "views3")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val isTextSearch = response.request.url.queryParameter("s") != null

        if (!isTextSearch) return popularMangaParse(response)

        val mangas = document.select("div.c-tabs-item__content").map { element ->
            SManga.create().apply {
                val a = element.selectFirst(".post-title a") ?: element.selectFirst("a")!!
                setUrlWithoutDomain(a.attr("href"))
                title = a.text().takeIf { it.isNotEmpty() }
                    ?: element.selectFirst("a")?.attr("title")
                    ?: throw Exception("Missing title for manga at ${a.attr("href")}")
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("a.next.page-numbers, a.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList = FilterList()

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("p.titleMangaSingle")?.text()
                ?: document.selectFirst(".post-title h1, .post-title h3")?.text() ?: ""
            thumbnail_url = document.selectFirst(".thumble-container img")?.imgAttr()
                ?: document.selectFirst(".summary_image img")?.imgAttr()

            description = document.selectFirst("#section-sinopsis p.font-light.text-white")?.text()
                ?: document.selectFirst(".summary__content")?.text()

            val genresList = document.select("#section-sinopsis div:contains(Generos:) + div a").map { it.text() }
            genre = if (genresList.isNotEmpty()) {
                genresList.joinToString()
            } else {
                document.select(".genres-content a").joinToString { it.text() }
            }

            author = document.selectFirst("#section-sinopsis div:contains(Autor:) + div a")?.text()
                ?: document.selectFirst(".author-content a")?.text()

            val statusText = document.selectFirst(".btn-completed")?.text()
                ?: document.selectFirst(".btn-ongoing")?.text()
                ?: document.selectFirst("button:contains(Finalizado), button:contains(En curso)")?.text()
                ?: document.selectFirst(".post-status .summary-content")?.text()
                ?: ""

            status = when {
                statusText.contains("en curso", ignoreCase = true) -> SManga.ONGOING
                statusText.contains("finalizado", ignoreCase = true) -> SManga.COMPLETED
                statusText.contains("ongoing", ignoreCase = true) -> SManga.ONGOING
                statusText.contains("completed", ignoreCase = true) -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val mangaId = document.selectFirst("script:containsData(manga_id)")
            ?.data()
            ?.let { Regex(""""manga_id"\s*:\s*"(\d+)"""").find(it)?.groupValues?.get(1) }
            ?: throw Exception("No se pudo encontrar el ID del manga")

        val form = FormBody.Builder()
            .add("action", "plot_anti_hack")
            .add("page", "2")
            .add("mangaid", mangaId)
            .add("secret", "mihonsuckmydick")
            .build()

        val apiResponse = client.newCall(
            POST("$baseUrl/wp-json/plot/v1/getcaps7", headers, form),
        ).execute()

        val apiData = apiResponse.parseAs<ChapterApiResponse>()

        val mangaPath = response.request.url.encodedPath

        return apiData.manga.flatMap { volume ->
            volume.chapters.map { chapter ->
                SChapter.create().apply {
                    setUrlWithoutDomain("$mangaPath${chapter.chapterSlug}/")
                    name = buildString {
                        append("Capítulo ${chapter.chapterName}")
                        if (chapter.chapterNameExtend.isNotEmpty()) {
                            append(" - ${chapter.chapterNameExtend}")
                        }
                    }
                    date_upload = dateFormat.tryParse(chapter.date)
                }
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.page-break img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
        else -> attr("abs:src")
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }
}
