package eu.kanade.tachiyomi.multisrc.otakusanctuary

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.select.Elements
import org.jsoup.select.Evaluator
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

open class OtakuSanctuary(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = false

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    private val helper = OtakuSanctuaryHelper(lang)

    private val json: Json by injectLazy()

    // There's no popular list, this will have to do
    override fun popularMangaRequest(page: Int) = POST(
        "$baseUrl/Manga/Newest",
        headers,
        FormBody.Builder().apply {
            add("Lang", helper.otakusanLang())
            add("Page", page.toString())
            add("Type", "Include")
            add("Dir", "NewPostedDate")
        }.build(),
    )

    private fun parseMangaCollection(elements: Elements): List<SManga> {
        val page = emptyList<SManga>().toMutableList()

        for (element in elements) {
            val url = element.select("div.mdl-card__title a").first()!!.attr("abs:href")
            // ignore external chapters
            if (url.toHttpUrl().host != baseUrl.toHttpUrl().host) {
                continue
            }

            // ignore web novels/light novels
            val variant = element.select("div.mdl-card__supporting-text div.text-overflow-90 a").text()
            if (variant.contains("Novel")) {
                continue
            }

            // ignore languages that dont match current ext
            val language = element.select("img.flag").attr("abs:src")
                .substringAfter("flags/")
                .substringBefore(".png")
            if (helper.otakusanLang() != "all" && language != helper.otakusanLang()) {
                continue
            }

            page += SManga.create().apply {
                setUrlWithoutDomain(url)
                title = element.select("div.mdl-card__supporting-text a[target=_blank]").text()
                    .replaceFirstChar { it.titlecase() }
                thumbnail_url = element.select("div.container-3-4.background-contain img").first()!!.attr("abs:src")
            }
        }
        return page
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val collection = document.select("div.mdl-card")
        val hasNextPage = !document.select("button.btn-loadmore").text().contains("Hết")
        return MangasPage(parseMangaCollection(collection), hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegments("Home/Search")
                addQueryParameter("search", query)
            }.build().toString(),
            headers,
        )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val collection = document.select("div.collection:has(.group-header:contains(Manga)) div.mdl-card")
        return MangasPage(parseMangaCollection(collection), false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.select("h1.title.text-lg-left.text-overflow-2-line")
                .text()
                .replaceFirstChar { it.titlecase() }
            author = document.select("tr:contains(Tác Giả) a.capitalize").first()!!.text()
                .replaceFirstChar { it.titlecase() }
            description = document.select("div.summary p").joinToString("\n") {
                it.run {
                    select(Evaluator.Tag("br")).prepend("\\n")
                    this.text().replace("\\n", "\n").replace("\n ", "\n")
                }
            }.trim()
            genre = document.select("div.genres a").joinToString { it.text() }
            thumbnail_url = document.select("div.container-3-4.background-contain img").attr("abs:src")

            val statusString = document.select("tr:contains(Tình Trạng) td").first()!!.text().trim()
            status = when (statusString) {
                "Ongoing" -> SManga.ONGOING
                "Done" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private fun parseDate(date: String): Long {
        if (date.contains("cách đây")) {
            val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
            val cal = Calendar.getInstance()

            return when {
                date.contains("ngày") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                date.contains("tiếng") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
                date.contains("phút") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
                date.contains("giây") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
                else -> 0L
            }
        } else {
            return runCatching { dateFormat.parse(date)?.time }.getOrNull() ?: 0L
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("tr.chapter").map {
            val cells = it.select("td")
            SChapter.create().apply {
                setUrlWithoutDomain(cells[1].select("a").attr("href"))
                name = cells[1].text()
                date_upload = parseDate(cells[3].text())
                chapter_number = cells[0].text().toFloatOrNull() ?: -1f
            }
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val vi = document.select("#dataip").attr("value")
        val numericId = document.select("#inpit-c").attr("data-chapter-id")

        val data = json.parseToJsonElement(
            client.newCall(
                POST(
                    "$baseUrl/Manga/UpdateView",
                    headers,
                    FormBody.Builder().add("chapId", numericId).build(),
                ),
            ).execute().body.string(),
        ).jsonObject

        if (data["view"] != null) {
            val usingservers = mutableListOf(0, 0, 0)

            val isSuccess = data["isSuccess"]!!.jsonArray.map { it.jsonPrimitive.content }
            return json.parseToJsonElement(data["view"]!!.jsonPrimitive.content).jsonArray.mapIndexed { idx, it ->
                var url = helper.processUrl(it.jsonPrimitive.content).removePrefix("image:")
                val indexServer = getIndexLessServer(usingservers)

                if (url.contains("ImageSyncing") || url.contains("FetchService") || url.contains("otakusan.net_") && (url.contains("extendContent") || url.contains("/Extend")) && !url.contains("fetcher.otakusan.net") && !url.contains("image3.otakusan.net") && !url.contains("image3.otakuscan.net") && !url.contains("[GDP]") && !url.contains("[GDT]")) {
                    if (url.startsWith("/api/Value/")) {
                        val serverUrl = if (helper.otakusanLang() == "us" && indexServer == 1) {
                            US_SERVERS[0]
                        } else {
                            SERVERS[indexServer]
                        }
                        url = "$serverUrl$url"
                    }

                    if (url.contains("otakusan.net_") && !url.contains("fetcher.otakuscan.net")) {
                        url += "#${isSuccess[idx]}"
                    }

                    usingservers[indexServer] += 1
                }

                Page(idx, imageUrl = url)
            }
        } else {
            val alternate = json.parseToJsonElement(
                client.newCall(
                    POST(
                        "$baseUrl/Manga/CheckingAlternate",
                        headers,
                        FormBody.Builder().add("chapId", numericId).build(),
                    ),
                ).execute().body.string(),
            ).jsonObject
            val content = alternate["Content"]?.jsonPrimitive?.content
                ?: throw Exception("No pages found")
            return json.parseToJsonElement(content).jsonArray.mapIndexed { idx, it ->
                Page(idx, imageUrl = helper.processUrl(it.jsonPrimitive.content, vi))
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        val url = request.url.toString()

        val newRequest = request.newBuilder()

        if (url.contains("ImageSyncing") || url.contains("FetchService") || url.contains("otakusan.net_") && (url.contains("extendContent") || url.contains("/Extend")) && !url.contains("fetcher.otakusan.net") && !url.contains("image3.otakusan.net") && !url.contains("image3.otakuscan.net") && !url.contains("[GDP]") && !url.contains("[GDT]")) {
            if (url.contains("otakusan.net_") && !url.contains("fetcher.otakuscan.net")) {
                newRequest.header("page-sign", request.url.fragment!!)
            } else {
                newRequest.header("page-lang", "vn-lang")
            }
        }

        return newRequest.build()
    }

    private fun getIndexLessServer(usingservers: List<Int>): Int {
        var minIndex = usingservers[0]
        var minNumber = usingservers[0]
        for (i in 1 until 3) {
            if (usingservers[i] <= minNumber) {
                minIndex = i
                minNumber = usingservers[i]
            }
        }
        return minIndex
    }

    companion object {
        val SERVERS = listOf("https://image2.otakuscan.net", "https://shopotaku.net", "https://image.otakuscan.net")
        val US_SERVERS = listOf("https://image3.shopotaku.net", "https://image2.otakuscan.net")
    }
}
