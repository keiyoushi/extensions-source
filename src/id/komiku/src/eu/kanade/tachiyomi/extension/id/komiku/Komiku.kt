package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Source
abstract class Komiku : HttpSource() {

    private val apiUrl = "https://api.komiku.org"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor(::headersInterceptor)
        .rateLimit(2)
        .build()

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = GET(mangaApiUrlBuilder(page).addQueryParameter("orderby", "meta_value_num").build(), headers)

    override fun popularMangaParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = GET(mangaApiUrlBuilder(page).addQueryParameter("orderby", "modified").build(), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = mangaListParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = mangaApiUrlBuilder(page).apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            }

            filters.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }
        }.build()

        return GET(url, headers)
    }

    private fun mangaApiUrlBuilder(page: Int) = apiUrl.toHttpUrl().newBuilder().apply {
        addPathSegment("manga")
        if (page > 1) {
            addPathSegments("page/$page")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.code == 404) return MangasPage(emptyList(), false)
        return mangaListParse(response)
    }

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            description = buildString {
                append(document.select("#Sinopsis > p").text())

                document.selectFirst("table.inftable tr:contains(Judul Indonesia) td + td")?.text()?.let {
                    if (it.isNotEmpty()) {
                        if (isNotEmpty()) append("\n\n")
                        append("Judul Indonesia: $it")
                    }
                }
            }

            author = document.selectFirst("table.inftable td:contains(Pengarang)+td, table.inftable td:contains(Komikus)+td")?.text()
            genre = document.select("ul.genre li.genre a span").joinToString { it.text() }.takeIf { it.isNotEmpty() }
            status = parseStatus(document.selectFirst("table.inftable tr > td:contains(Status) + td")?.text())
            thumbnail_url = document.selectFirst("div.ims > img")?.absUrl("src")?.removeQuery()
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", true) || status.contains("On Going", true) -> SManga.ONGOING
        status.contains("End", true) || status.contains("Completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================= Chapters ===============================
    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#Daftar_Chapter tr:has(td.judulseries)").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a")!!
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.text()

                val timeStamp = element.selectFirst("td.tanggalseries")?.text().orEmpty()
                date_upload = if (timeStamp.contains("lalu")) {
                    parseRelativeDate(timeStamp)
                } else {
                    dateFormat.tryParse(timeStamp)
                }
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)

    // Used Google translate here
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.substringBefore(" lalu").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "jam" -> calendar.add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt())
            "menit" -> calendar.add(Calendar.MINUTE, -trimmedDate[0].toInt())
            "detik" -> calendar.add(Calendar.SECOND, 0)
        }

        return calendar.timeInMillis
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val url = response.request.url.toString()
        return document.select("#Baca_Komik img").mapIndexed { i, element ->
            Page(i, url, element.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page): Request {
        val headers = headersBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, headers)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Type(),
        Order(),
        Genre1(),
        Genre2(),
        Status(),
    )

    // ============================= Utilities ==============================
    private fun headersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val urlString = url.toString()

        if (urlString.contains("komiku.org") || urlString.contains("komikid.org")) {
            val newHeaders = request.headers.newBuilder().apply {
                removeAll("X-Requested-With")
                set("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")

                if (url.host.contains("img") || url.host.contains("thumbnail") || url.host.contains("update")) {
                    val referer = request.header("Referer")
                    if (referer == null || !referer.contains(baseUrl)) {
                        set("Referer", "$baseUrl/")
                    }
                    set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                    set("Sec-Fetch-Dest", "image")
                    set("Sec-Fetch-Mode", "no-cors")
                    set("Sec-Fetch-Site", if (urlString.contains("komiku.org")) "same-site" else "cross-site")
                }
            }.build()

            return chain.proceed(
                request.newBuilder()
                    .headers(newHeaders)
                    .build(),
            )
        }

        return chain.proceed(request)
    }

    private fun mangaListParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.bge").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                setUrlWithoutDomain(element.selectFirst("a:has(h3)")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.absUrl("src")?.removeQuery()
            }
        }
        val hasNextPage = document.selectFirst("span[hx-get]") != null || mangas.size >= 10
        return MangasPage(mangas, hasNextPage)
    }

    private fun String.removeQuery() = if (isEmpty()) this else toHttpUrl().newBuilder().query(null).build().toString()
}
