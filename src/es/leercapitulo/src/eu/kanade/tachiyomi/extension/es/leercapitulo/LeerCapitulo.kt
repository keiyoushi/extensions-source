package eu.kanade.tachiyomi.extension.es.leercapitulo

import android.util.Base64
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.nio.charset.Charset

class LeerCapitulo : ParsedHttpSource() {
    override val name = "LeerCapitulo"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val baseUrl = "https://www.leercapitulo.co"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 3)
        .build()

    private val notRateLimitClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = ".hot-manga > .thumbnails > a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.attr("title")
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search-autocomplete")
            urlBuilder.addQueryParameter("term", query)

            return GET(urlBuilder.build(), headers)
        } else {
            for (filter in filters) {
                when (filter) {
                    is GenreFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addPathSegment("genre")
                            urlBuilder.addPathSegment(filter.toUriPart())
                            break
                        }
                    }
                    is AlphabeticFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addPathSegment("initial")
                            urlBuilder.addPathSegment(filter.toUriPart())
                            break
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            urlBuilder.addPathSegment("status")
                            urlBuilder.addPathSegment(filter.toUriPart())
                            break
                        }
                    }
                    else -> {}
                }
            }
            urlBuilder.addPathSegment("") // Empty path segment to avoid 404
            urlBuilder.addQueryParameter("page", page.toString())
        }
        val url = urlBuilder.build()
        if (url.pathSegments.size <= 1) throw Exception("Debe seleccionar un filtro o realizar una búsqueda por texto.")

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.pathSegments.contains("search-autocomplete")) {
            return super.searchMangaParse(response)
        }

        val mangas = json.decodeFromString<List<MangaDto>>(response.body.string()).map {
            SManga.create().apply {
                setUrlWithoutDomain(it.link)
                title = it.label
                thumbnail_url = baseUrl + it.thumbnail
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaSelector(): String = "div.cate-manga div.mainpage-manga"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.media-body a")!!.attr("href"))
        title = element.selectFirst("div.media-body a")!!.text()
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    override fun searchMangaNextPageSelector(): String = "ul.pagination > li.active + li"

    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Los filtros serán ignorados si se realiza una búsqueda por texto."),
            Filter.Header("Los filtros no se pueden combinar  entre ellos."),
            GenreFilter(),
            AlphabeticFilter(),
            StatusFilter(),
        )
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = ".mainpage-manga"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst(".media-body > a")!!.attr("abs:href"))
        title = element.selectFirst("h4")!!.text()
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()

        val altNames = document.selectFirst(".description-update > span:contains(Títulos Alternativos:) + :matchText")?.text()
        val desc = document.selectFirst("#example2")!!.text()
        description = when (altNames) {
            null -> desc
            else -> "$desc\n\nAlt name(s): $altNames"
        }

        genre = document.select(".description-update a[href^='/genre/']").joinToString { it.text() }
        status = document.selectFirst(".description-update > span:contains(Estado:) + :matchText")!!.text().toStatus()
        thumbnail_url = document.selectFirst(".cover-detail > img")!!.imgAttr()
    }

    override fun chapterListSelector(): String = ".chapter-list > ul > li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        with(element.selectFirst("a.xanh")!!) {
            setUrlWithoutDomain(attr("abs:href"))
            name = text()
        }
    }

    private var cachedScriptUrl: String? = null
    override fun pageListParse(document: Document): List<Page> {
        val orderList = document.selectFirst("meta[property=ad:check]")?.attr("content")
            ?.replace(ORDER_LIST_REGEX, "-")
            ?.split("-")

        val useReversedString = orderList?.any { it == "01" }

        val arrayData = document.selectFirst("#array_data")!!.text()

        val scripts = document.select("head > script[src^=/assets/][src$=.js]").map { it.attr("abs:src") }.reversed().toMutableList()

        var dataScript: String? = null

        cachedScriptUrl?.let {
            if (scripts.remove(it)) {
                scripts.add(0, it)
            }
        }

        for (scriptUrl in scripts) {
            val scriptData = notRateLimitClient.newCall(GET(scriptUrl, headers)).execute().body.string()
            val deobfuscatedScript = Deobfuscator.deobfuscateScript(scriptData)
            if (deobfuscatedScript != null && deobfuscatedScript.contains("#array_data")) {
                dataScript = deobfuscatedScript
                cachedScriptUrl = scriptUrl
                break
            }
        }

        if (dataScript == null) throw Exception("Unable to find the script")

        val (key1, key2) = KEY_REGEX.findAll(dataScript).map { it.groupValues[1] }.toList()

        val encodedUrls = arrayData.replace(DECODE_REGEX) {
            val index = key2.indexOf(it.value)
            key1[index].toString()
        }

        val urlList = String(Base64.decode(encodedUrls, Base64.DEFAULT), Charset.forName("UTF-8")).split(",")

        val sortedUrls = orderList?.map {
            if (useReversedString == true) urlList[it.reversed().toInt()] else urlList[it.toInt()]
        }?.reversed() ?: urlList

        return sortedUrls.mapIndexed { i, image_url ->
            Page(i, imageUrl = image_url)
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException()

    private fun String.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Paused" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    @Serializable
    class MangaDto(
        val label: String,
        val link: String,
        val thumbnail: String,
    )

    companion object {
        private val ORDER_LIST_REGEX = "[^\\d]+".toRegex()
        private val KEY_REGEX = """'([A-Z0-9]{62})'""".toRegex(RegexOption.IGNORE_CASE)
        private val DECODE_REGEX = Regex("[A-Z0-9]", RegexOption.IGNORE_CASE)
    }
}
