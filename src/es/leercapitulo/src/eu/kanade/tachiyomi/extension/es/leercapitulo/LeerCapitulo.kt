package eu.kanade.tachiyomi.extension.es.leercapitulo

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.synchrony.Deobfuscator
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.nio.charset.Charset

class LeerCapitulo : HttpSource() {
    override val name = "LeerCapitulo"

    override val lang = "es"

    override val supportsLatest = true

    override val baseUrl = "https://www.leercapitulo.co"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 3)
        .build()

    private val notRateLimitClient = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".hot-manga > .thumbnails > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = baseUrl.toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addPathSegment("search-autocomplete")
            urlBuilder.addQueryParameter("term", query)

            return GET(urlBuilder.build(), headers)
        }

        filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addPathSegment("genre")
            urlBuilder.addPathSegment(it.toUriPart())
        } ?: filters.firstInstanceOrNull<AlphabeticFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addPathSegment("initial")
            urlBuilder.addPathSegment(it.toUriPart())
        } ?: filters.firstInstanceOrNull<StatusFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addPathSegment("status")
            urlBuilder.addPathSegment(it.toUriPart())
        }

        urlBuilder.addPathSegment("") // Empty path segment to avoid 404
        urlBuilder.addQueryParameter("page", page.toString())

        val url = urlBuilder.build()
        if (url.pathSegments.size <= 1) {
            throw Exception("Debe seleccionar un filtro o realizar una búsqueda por texto.")
        }

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.contains("search-autocomplete")) {
            val mangas = response.parseAs<List<Dto>>().map { it.toSManga() }
            return MangasPage(mangas, hasNextPage = false)
        }

        val document = response.asJsoup()
        val mangas = document.select("div.cate-manga div.mainpage-manga").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("div.media-body a")!!.attr("abs:href"))
                title = element.selectFirst("div.media-body a")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination > li.active + li") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Los filtros no se pueden combinar  entre ellos."),
        GenreFilter(),
        AlphabeticFilter(),
        StatusFilter(),
    )

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".mainpage-manga").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst(".media-body > a")!!.attr("abs:href"))
                title = element.selectFirst("h4")!!.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()

            val altNames = document.selectFirst(".description-update > span:contains(Títulos Alternativos:) + :matchText")?.text()
            val desc = document.selectFirst("#example2")?.text()
            description = buildString {
                if (!desc.isNullOrEmpty()) append(desc)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alt name(s): ")
                    append(altNames)
                }
            }

            genre = document.select(".description-update a[href^='/genre/']").joinToString { it.text() }
            status = document.selectFirst(".description-update > span:contains(Estado:) + :matchText")?.text()?.toStatus() ?: SManga.UNKNOWN
            thumbnail_url = document.selectFirst(".cover-detail > img")?.imgAttr()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-list > ul > li").map { element ->
            SChapter.create().apply {
                with(element.selectFirst("a.xanh")!!) {
                    setUrlWithoutDomain(attr("abs:href"))
                    name = text()
                }
            }
        }
    }

    private var cachedScriptUrl: String? = null

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val orderList = document.selectFirst("meta[property=ad:check]")?.attr("content")
            ?.replace(ORDER_LIST_REGEX, "-")
            ?.split("-")

        val useReversedString = orderList?.any { it == "01" } == true

        val arrayData = document.selectFirst("#array_data")!!.text()

        val scripts = document.select("head > script[src^=/assets/][src*=.js]")
            .map { it.attr("abs:src") }
            .reversed()
            .toMutableList()

        var dataScript: String? = null

        cachedScriptUrl?.let {
            if (scripts.remove(it)) {
                scripts.add(0, it)
            }
        }

        for (scriptUrl in scripts) {
            val scriptData = notRateLimitClient.newCall(GET(scriptUrl, headers)).execute().use { it.body.string() }
            val deobfuscatedScript = runCatching { Deobfuscator.deobfuscateScript(scriptData) }.getOrNull()
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
            if (useReversedString) urlList[it.reversed().toInt()] else urlList[it.toInt()]
        }?.reversed() ?: urlList

        return sortedUrls.mapIndexed { i, imageUrl ->
            Page(i, imageUrl = imageUrl)
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Dto.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(link)
        title = label
        thumbnail_url = baseUrl + thumbnail
    }

    private fun String.toStatus() = when (this) {
        "Ongoing" -> SManga.ONGOING
        "Paused" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val ORDER_LIST_REGEX = "[^\\d]+".toRegex()
        private val KEY_REGEX = """'([A-Z0-9]{62})'""".toRegex(RegexOption.IGNORE_CASE)
        private val DECODE_REGEX = Regex("[A-Z0-9]", RegexOption.IGNORE_CASE)
    }
}
