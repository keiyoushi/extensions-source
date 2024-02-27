package eu.kanade.tachiyomi.extension.ja.readmangaat

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class ReadMangaAt : ParsedHttpSource() {

    override val name = "ReadManga.at"

    override val baseUrl = "https://readmanga.at"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val ajaxUrl = "$baseUrl/wp-admin/admin-ajax.php"

    private val apiHeaders by lazy { apiHeadersBuilder().build() }

    private fun apiHeadersBuilder() = headersBuilder().apply {
        add("Accept", "application/json, text/javascript, */*; q=0.01")
        add("Host", baseUrl.toHttpUrl().host)
        add("Origin", baseUrl)
        add("X-Requested-With", "XMLHttpRequest")
    }

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("M月 d, yyyy", Locale.JAPANESE)

    private val chapterRegex = Regex("""\bp:\s*(\d+)""")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div:has(>div:contains(Popular)) > div > div[class~=entry]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        with(element.selectFirst("h4 > a")!!) {
            setUrlWithoutDomain(attr("abs:href"))
            title = text()
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return if (page > 1) {
            val apiBody = FormBody.Builder().apply {
                add("action", "z_do_ajax")
                add("_action", "loadmore")
                add("p", page.toString())
            }.build()

            POST(ajaxUrl, apiHeaders, apiBody)
        } else {
            GET(baseUrl, headers)
        }
    }

    override fun latestUpdatesSelector(): String = "div.row > div.col-sm-6"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        with(element.selectFirst("a:has(img)")!!) {
            thumbnail_url = selectFirst("img")!!.attr("abs:src")
            setUrlWithoutDomain(attr("abs:href"))
            title = attr("title")
        }
    }

    override fun latestUpdatesNextPageSelector(): String = ".text-center > a:contains(Load More)"

    override fun latestUpdatesParse(response: Response): MangasPage {
        if (response.request.method == "GET") {
            return super.latestUpdatesParse(response)
        }

        val data = response.parseAs<LatestDto>()
        val mangaList = Jsoup.parseBodyFragment(data.mes)
            .select("div.col-sm-6")
            .map(::latestUpdatesFromElement)
        val hasNextPage = data.going == "yes"

        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val genreFilter = filters.filterIsInstance<GenreFilter>().first()

            if (query.isNotBlank()) {
                addQueryParameter("s", query)
            } else {
                addPathSegment("genres")
                addEncodedPathSegment(genreFilter.toUriPart())
            }

            addPathSegment("")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("")
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = ".row > .col-6 > .entry-ma"

    override fun searchMangaFromElement(element: Element): SManga =
        latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String = ".pagination > span.current + a"

    // =============================== Filters ==============================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Note: ignored when using text search"),
        Filter.Separator(),
        GenreFilter(),
    )

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    class GenreFilter : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("Ecchi", "ecchi"),
            Pair("SF.ファンタジー", "sf-%e3%83%95%e3%82%a1%e3%83%b3%e3%82%bf%e3%82%b8%e3%83%bc"),
            Pair("お嬢様・令嬢", "%e3%81%8a%e5%ac%a2%e6%a7%98%e3%83%bb%e4%bb%a4%e5%ac%a2"),
            Pair("アドベンチャ", "%e3%82%a2%e3%83%89%e3%83%99%e3%83%b3%e3%83%81%e3%83%a3"),
            Pair("ゲーム", "%e3%82%b2%e3%83%bc%e3%83%a0"),
            Pair("サブカル・個性派", "%e3%82%b5%e3%83%96%e3%82%ab%e3%83%ab%e3%83%bb%e5%80%8b%e6%80%a7%e6%b4%be"),
            Pair("ショタコン", "%e3%82%b7%e3%83%a7%e3%82%bf%e3%82%b3%e3%83%b3"),
            Pair("バスケットボール", "%e3%83%90%e3%82%b9%e3%82%b1%e3%83%83%e3%83%88%e3%83%9c%e3%83%bc%e3%83%ab"),
            Pair("バトル", "%e3%83%90%e3%83%88%e3%83%ab"),
            Pair("バレーボール", "%e3%83%90%e3%83%ac%e3%83%bc%e3%83%9c%e3%83%bc%e3%83%ab"),
            Pair("ピューター", "%e3%83%94%e3%83%a5%e3%83%bc%e3%82%bf%e3%83%bc"),
            Pair("ミステリー・サスペンス", "%e3%83%9f%e3%82%b9%e3%83%86%e3%83%aa%e3%83%bc%e3%83%bb%e3%82%b5%e3%82%b9%e3%83%9a%e3%83%b3%e3%82%b9"),
            Pair("ラブラブ・あまあま", "%e3%83%a9%e3%83%96%e3%83%a9%e3%83%96%e3%83%bb%e3%81%82%e3%81%be%e3%81%82%e3%81%be"),
            Pair("三角関係", "%e4%b8%89%e8%a7%92%e9%96%a2%e4%bf%82"),
            Pair("会社", "%e4%bc%9a%e7%a4%be"),
            Pair("俺様・S彼", "%e4%bf%ba%e6%a7%98%e3%83%bbs%e5%bd%bc"),
            Pair("兄弟", "%e5%85%84%e5%bc%9f"),
            Pair("制服", "%e5%88%b6%e6%9c%8d"),
            Pair("前世", "%e5%89%8d%e4%b8%96"),
            Pair("天使・悪魔", "%e5%a4%a9%e4%bd%bf%e3%83%bb%e6%82%aa%e9%ad%94"),
            Pair("妊婦", "%e5%a6%8a%e5%a9%a6"),
            Pair("宗教", "%e5%ae%97%e6%95%99"),
            Pair("悪役令嬢", "%e6%82%aa%e5%bd%b9%e4%bb%a4%e5%ac%a2"),
            Pair("擬人化", "%e6%93%ac%e4%ba%ba%e5%8c%96"),
            Pair("短編", "%e7%9f%ad%e7%b7%a8"),
            Pair("職業・ビジネス", "%e8%81%b7%e6%a5%ad%e3%83%bb%e3%83%93%e3%82%b8%e3%83%8d%e3%82%b9"),
            Pair("致命的な", "%e8%87%b4%e5%91%bd%e7%9a%84%e3%81%aa"),
            Pair("萌え", "%e8%90%8c%e3%81%88"),
            Pair("超能力日常", "%e8%b6%85%e8%83%bd%e5%8a%9b%e6%97%a5%e5%b8%b8"),
            Pair("電子特典付き", "%e9%9b%bb%e5%ad%90%e7%89%b9%e5%85%b8%e4%bb%98%e3%81%8d"),
        ),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst(".main-thumb > img[src]")!!.attr("abs:src")

        with(document.selectFirst("div.row[class~=mb] > .col")!!) {
            title = selectFirst(".name")!!.text()
            status = selectFirst("span").parseStatus()
        }

        val id = document.selectFirst("a[data-t]")!!.attr("data-t")
        val apiHeaders = apiHeadersBuilder().apply {
            set("Referer", document.location())
        }.build()

        // Genres
        val genresBody = FormBody.Builder().apply {
            add("action", "z_do_ajax")
            add("_action", "load_all_genres")
            add("t", id)
        }.build()

        val genreDto = client.newCall(
            POST(ajaxUrl, apiHeaders, genresBody),
        ).execute().parseAs<InfoDto>()
        genre = Jsoup.parseBodyFragment(genreDto.mes)
            .select("a")
            .joinToString { it.text() }

        // Desc
        val descBody = FormBody.Builder().apply {
            add("action", "z_do_ajax")
            add("_action", "load_tag_desc")
            add("t", id)
        }.build()
        description = client.newCall(
            POST(ajaxUrl, apiHeaders, descBody),
        ).execute().parseAs<InfoDto>().mes
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "dropped" -> SManga.CANCELLED
        "paused" -> SManga.ON_HIATUS
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListSelector(): String = ".row > .col-sm-6 > .entry-chapter"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        date_upload = try {
            element.selectFirst(".date")?.text()?.let {
                dateFormat.parse(it)!!.time
            } ?: 0L
        } catch (_: ParseException) {
            0L
        }
        with(element.selectFirst("a")!!) {
            name = text()
            setUrlWithoutDomain(attr("abs:href"))
        }
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        val script = document.selectFirst("script:containsData(decode_images)")?.data()
            ?: throw Exception("Unable to find script")
        val id = chapterRegex.find(script)?.groupValues?.get(1)
            ?: throw Exception("Unable to get chapter id")

        var going = 1
        var imgIndex = 0
        val pageList = mutableListOf<Page>()
        val apiHeaders = apiHeadersBuilder().apply {
            set("Referer", document.location())
        }.build()

        while (going == 1) {
            val pagesBody = FormBody.Builder().apply {
                add("action", "z_do_ajax")
                add("_action", "decode_images")
                add("p", id)
                add("img_index", imgIndex.toString())
            }.build()

            val data = client.newCall(
                POST(ajaxUrl, apiHeaders, pagesBody),
            ).execute().parseAs<ImageResponseDto>()

            imgIndex = data.imgIndex
            going = if (data.mes.isNotBlank()) data.going else 0

            Jsoup.parseBodyFragment(data.mes).select("img[src]").forEachIndexed { index, element ->
                pageList.add(Page(imgIndex + index, imageUrl = element.attr("abs:src")))
            }
        }

        return pageList
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,*/*")
            add("Accept-Language", "en-US,en;q=0.5")
            add("DNT", "1")
            add("Host", page.imageUrl!!.substringAfter("://").substringBefore("/"))
            add("Sec-Fetch-Dest", "image")
            add("Sec-Fetch-Mode", "no-cors")
            add("Sec-Fetch-Site", "cross-site")
        }.build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    // ============================= Utilities ==============================

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
}
