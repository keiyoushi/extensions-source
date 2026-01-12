package eu.kanade.tachiyomi.extension.tr.sadscans

import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Sadscans : HttpSource() {
    override val name = "Sadscans"
    override val baseUrl = "https://sadscans.net"
    override val lang = "tr"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int): Request {
        val rawJsonInput = """{"0":{"json":{"search":null,"type":null,"status":null,"sort":"views_desc","views":null,"page":$page,"limit":15},"meta":{"values":{"search":["undefined"],"type":["undefined"],"status":["undefined"],"views":["undefined"]},"v":1}}}"""

        val url = "$baseUrl/api/trpc/srs.getSeries".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", rawJsonInput)
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<List<SadScansSearch>>(response.body.string())
        val dataList = responseData.firstOrNull()?.result?.data?.json ?: emptyList()

        val mangas = dataList.map { item ->
            SManga.create().apply {
                title = Jsoup.parse(item.name ?: "").text()
                url = item.href ?: ""
                thumbnail_url = item.thumb?.let {
                    if (it.startsWith("/")) "$baseUrl$it" else it
                }
                author = item.author
                status = item.status.parseStatus()
            }
        }

        return MangasPage(mangas, mangas.size >= 2)
    }

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section.guncel-bolumler div.container div.group\\/card").map { element ->
            SManga.create().apply {
                element.selectFirst("a[href*=/seriler]")?.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                }
                title = element.selectFirst("img")?.attr("alt") ?: "Bilinmeyen Seri"
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // Search Manga
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val rawJsonInput = """{"0":{"json":{"search":"$query","page":$page,"limit":15}}}"""
        val url = "$baseUrl/api/trpc/srs.getSeries".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", rawJsonInput)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<List<SadScansSearch>>(response.body.string())

        val mangas = responseData.firstOrNull()?.result?.data?.json?.map { item ->
            SManga.create().apply {
                title = item.name ?: ""
                url = item.href ?: ""
                thumbnail_url = item.thumb?.let {
                    if (it.startsWith("/")) "$baseUrl$it" else it
                }
                author = item.author
                status = item.status.parseStatus()
            }
        } ?: emptyList()

        return MangasPage(mangas, mangas.size >= 15)
    }

    // Manga Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val script = document.selectFirst("script#webpage-structured-data")?.data() ?: ""

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()
                ?: document.selectFirst(".title")!!.text()
            author = document.select("span:contains(Yazar) + span").text()
            artist = author
            status = document.select("span:contains(Durum) + span").text().parseStatus()
            description = script.substringAfter("\"description\":\"").substringBefore("\",")
            thumbnail_url = document.select("meta[property=og:image]").attr("content")
                .ifEmpty { document.select("div.relative img").attr("abs:src") }
        }
    }

    // Chapter List
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.fromCallable {
            val slug = manga.url.trim('/').substringAfterLast('/')
            val seriesId = manga.thumbnail_url?.let { url ->
                Regex("""/series/([a-zA-Z0-9]+)/""").find(url)?.groupValues?.get(1)
            } ?: ""

            val allChapters = mutableListOf<SChapter>()
            var page = 1
            var totalPages = 1

            do {
                val rawJsonInput = """{"0":{"json":{"sef":"$slug","page":$page,"limit":20}},"1":{"json":{"seriesId":"$seriesId","userId":null},"meta":{"values":{"userId":["undefined"]}}}}"""

                val url = "$baseUrl/api/trpc/srsDtl.getSeriesData,reviews.getSeriesReviews".toHttpUrl().newBuilder()
                    .addQueryParameter("batch", "1")
                    .addQueryParameter("input", rawJsonInput)
                    .build()

                val response = client.newCall(GET(url, headers)).execute()

                if (!response.isSuccessful) {
                    response.close()
                    if (page == 1) throw Exception("API Hatası: ${response.code}")
                    break
                }

                val trpcResponseList = json.decodeFromString<List<TrpcResponse>>(response.body.string())
                val firstItemJsonElement = trpcResponseList.firstOrNull()?.result?.data?.json ?: break

                val seriesData = json.decodeFromJsonElement<SeriesDataDto>(firstItemJsonElement)

                if (page == 1) {
                    totalPages = seriesData.pagination?.totalPages ?: 1
                }

                val parsedChapters = seriesData.chapters?.map { dto ->
                    SChapter.create().apply {
                        val chapterNumber = dto.no?.toString()?.removeSuffix(".0") ?: ""
                        name = "Bölüm $chapterNumber" + (if (!dto.name.isNullOrEmpty()) " - ${dto.name}" else "")
                        setUrlWithoutDomain(dto.href ?: "")
                        date_upload = parseIsoDate(dto.date)
                    }
                } ?: emptyList()

                allChapters.addAll(parsedChapters)
                page++
            } while (page <= totalPages)

            allChapters
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = emptyList()

    // Page List
    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val startToken = "\\\"images\\\":["
        val startIndex = html.indexOf(startToken)
        if (startIndex == -1) return emptyList()

        val arrayStartIndex = startIndex + startToken.length - 1
        val endToken = "],\\\""
        val endIndex = html.indexOf(endToken, arrayStartIndex)
        if (endIndex == -1) return emptyList()

        val rawArrayEscaped = html.substring(arrayStartIndex, endIndex + 1)
        val jsonArrayString = rawArrayEscaped.replace("\\\"", "\"")

        val pageList = json.decodeFromString<List<SadScansPageDto>>(jsonArrayString)

        return pageList.mapIndexed { index, dto ->
            val url = dto.src ?: ""
            val fullUrl = if (url.startsWith("/")) "$baseUrl$url" else url
            Page(index, imageUrl = fullUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Helper Functions
    private fun parseIsoDate(dateStr: String?): Long {
        return dateStr?.let {
            runCatching { isoDateFormat.parse(it)!!.time }.getOrNull()
        } ?: 0L
    }

    private fun String?.parseStatus(): Int = when (this?.lowercase()) {
        "devam ediyor", "ongoing" -> SManga.ONGOING
        "tamamlandı", "completed", "bitti" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
