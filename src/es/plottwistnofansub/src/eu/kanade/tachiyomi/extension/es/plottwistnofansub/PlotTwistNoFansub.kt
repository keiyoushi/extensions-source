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
            addPathSegment("biblioteca3")
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
                setUrlWithoutDomain(a.attr("abs:href").ifEmpty { a.attr("href") })
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
            addPathSegment("biblioteca3")
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
            url.addPathSegment("biblioteca3")
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
                setUrlWithoutDomain(a.attr("abs:href").ifEmpty { a.attr("href") })
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
            title = document.selectFirst("p.mn-title-block")?.text()
                ?: document.selectFirst("p.titleMangaSingle")?.text()
                ?: document.selectFirst(".post-title h1, .post-title h3")?.text()
                ?: throw Exception("Manga title not found")

            thumbnail_url = document.selectFirst(".mn-cover-frame img")?.imgAttr()
                ?: document.selectFirst(".thumble-container img")?.imgAttr()
                ?: document.selectFirst(".summary_image img")?.imgAttr()

            description = document.selectFirst("h2:contains(Sinopsis) + div")?.text()
                ?: document.selectFirst("#section-sinopsis p.font-light.text-white")?.text()
                ?: document.selectFirst(".summary__content")?.text()

            val genresList = document.select("div.text-white:contains(Gé:) + div a, #section-sinopsis div:contains(Generos:) + div a").map { it.text() }
            genre = if (genresList.isNotEmpty()) {
                genresList.joinToString()
            } else {
                document.select(".genres-content a").joinToString { it.text() }
            }

            author = document.selectFirst("div.text-white:contains(Creación:) + div a, #section-sinopsis div:contains(Autor:) + div a")?.text()
                ?: document.selectFirst(".author-content a")?.text()

            val statusText = (
                document.selectFirst("button.mn-chip")?.text()
                    ?: document.selectFirst(".btn-completed")?.text()
                    ?: document.selectFirst(".btn-ongoing")?.text()
                    ?: document.selectFirst("button:contains(Finalizado), button:contains(En curso)")?.text()
                    ?: document.selectFirst(".post-status .summary-content")?.text()
                    ?: ""
                ).lowercase()

            status = when {
                statusText.contains("en emisión") || statusText.contains("en curso") || statusText.contains("ongoing") -> SManga.ONGOING
                statusText.contains("finalizado") || statusText.contains("completed") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val mangaId = document.selectFirst("script:containsData(mnWpMangaId)")
            ?.data()
            ?.let { MANGA_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("script:containsData(manga_id)")
                ?.data()
                ?.let { OLD_MANGA_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?: throw Exception("No se pudo encontrar el ID del manga")

        val scriptData = document.selectFirst("script:containsData(mnSeriesNavBundle)")?.data()

        val chapters = mutableListOf<SChapter>()

        // Fallback for new theme HTML chapters
        document.select(".contenedor-capitulo-miniatura a").forEach { a ->
            val url = a.attr("abs:href").ifEmpty { a.attr("href") }
            if (url.isNotEmpty()) {
                chapters.add(
                    SChapter.create().apply {
                        setUrlWithoutDomain(url)
                        val divs = a.select("div.text-sm")
                        val num = divs.getOrNull(0)?.text() ?: ""
                        val title = divs.getOrNull(1)?.text() ?: ""
                        name = buildString {
                            append("Capítulo $num")
                            if (title.isNotEmpty()) append(" - $title")
                        }
                        date_upload = dateFormat.tryParse(a.selectFirst("time")?.text()?.replace(HTML_TAG_REGEX, ""))
                    },
                )
            }
        }

        if (scriptData != null) {
            val navCsrf = CSRF_REGEX.find(scriptData)?.groupValues?.get(1)
                ?: throw Exception("No se pudo encontrar el token")

            val batchUrl = BATCH_URL_REGEX.find(scriptData)?.groupValues?.get(1)?.replace("\\/", "/")
                ?: throw Exception("No se pudo encontrar la URL de la API")

            val totalPagesText = document.selectFirst("script:containsData(totalPageCount)")?.data()
            val totalPages = totalPagesText?.let { TOTAL_PAGES_REGEX.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 1

            // First page is already fetched in HTML, begin on Page 2 if needed
            var page = if (chapters.isEmpty()) 1 else 2
            var hasNextPage = page <= totalPages

            while (hasNextPage) {
                val form = FormBody.Builder()
                    .add("page", page.toString())
                    .add("seriesPost", mangaId)
                    .add("navCsrf", navCsrf)
                    .build()

                val apiResponse = client.newCall(
                    POST(batchUrl, headers, form),
                ).execute()

                val apiData = apiResponse.parseAs<ChapterApiResponse>()

                if (apiData.chapters.isEmpty()) {
                    hasNextPage = false
                } else {
                    apiData.chapters.forEach { chapter ->
                        chapters.add(
                            SChapter.create().apply {
                                setUrlWithoutDomain(chapter.link)
                                name = buildString {
                                    append("Capítulo ${chapter.name}")
                                    if (chapter.nameExtend.isNotEmpty()) {
                                        append(" - ${chapter.nameExtend}")
                                    }
                                }
                                date_upload = dateFormat.tryParse(chapter.date.replace(HTML_TAG_REGEX, ""))
                            },
                        )
                    }
                    page++
                    if (page > totalPages) {
                        hasNextPage = false
                    }
                }
            }
        } else {
            // Old API method (fallback)
            val getcapsJson = document.selectFirst("script:containsData(plotGetcaps)")?.data()
            if (getcapsJson != null) {
                val secret = SECRET_REGEX.find(getcapsJson)?.groupValues?.get(1)
                    ?: throw Exception("No se pudo encontrar el secreto")

                val apiUrl = REST_URL_REGEX.find(getcapsJson)?.groupValues?.get(1)?.replace("\\/", "/")
                    ?: throw Exception("No se pudo encontrar la URL de la API")

                var page = 1
                var hasNextPage = true

                while (hasNextPage) {
                    val form = FormBody.Builder()
                        .add("action", "plot_anti_hack")
                        .add("page", page.toString())
                        .add("mangaid", mangaId)
                        .add("secret", secret)
                        .build()

                    val apiResponse = client.newCall(
                        POST(apiUrl, headers, form),
                    ).execute()

                    val apiData = apiResponse.parseAs<ChapterApiResponse>()

                    if (apiData.chapters.isEmpty()) {
                        hasNextPage = false
                    } else {
                        apiData.chapters.forEach { chapter ->
                            chapters.add(
                                SChapter.create().apply {
                                    setUrlWithoutDomain(chapter.link)
                                    name = buildString {
                                        append("Capítulo ${chapter.name}")
                                        if (chapter.nameExtend.isNotEmpty()) {
                                            append(" - ${chapter.nameExtend}")
                                        }
                                    }
                                    date_upload = dateFormat.tryParse(chapter.date.replace(HTML_TAG_REGEX, ""))
                                },
                            )
                        }
                        page++
                    }
                }
            }
        }

        return chapters
    }

    // =============================== Pages ================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div.pg-box img, div.page-break img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================= Utilities ==============================
    private fun Element.imgAttr(): String {
        val url = when {
            hasAttr("data-src") -> attr("abs:data-src").ifEmpty { attr("data-src") }
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src").ifEmpty { attr("data-lazy-src") }
            hasAttr("srcset") -> attr("abs:srcset").ifEmpty { attr("srcset") }.substringBefore(" ")
            else -> attr("abs:src").ifEmpty { attr("src") }
        }
        return url.trim()
    }

    private val dateFormat by lazy {
        SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH)
    }

    companion object {
        private val MANGA_ID_REGEX = Regex("""mnWpMangaId\s*=\s*(\d+)""")
        private val OLD_MANGA_ID_REGEX = Regex(""""manga_id"\s*:\s*"(\d+)"""")
        private val TOTAL_PAGES_REGEX = Regex("""totalPageCount\s*=\s*(\d+)""")
        private val SECRET_REGEX = Regex(""""secret"\s*:\s*"([^"]+)"""")
        private val REST_URL_REGEX = Regex(""""restUrl"\s*:\s*"([^"]+)"""")
        private val CSRF_REGEX = Regex(""""navCsrf"\s*:\s*"([^"]+)"""")
        private val BATCH_URL_REGEX = Regex(""""batchUrl"\s*:\s*"([^"]+)"""")
        private val HTML_TAG_REGEX = Regex("<[^>]*>")
    }
}
