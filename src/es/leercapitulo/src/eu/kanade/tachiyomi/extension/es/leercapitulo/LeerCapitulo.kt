package eu.kanade.tachiyomi.extension.es.leercapitulo

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.synchrony.Deobfuscator
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(1, 3.seconds) { it.host == baseUrlHost }
        .build()

    private val notRateLimitClient = network.client

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container .row a[href*='/leer/'], div[class*='col'] a[href*='/leer/']").mapNotNull { element ->
            val link = element.attr("abs:href")
            val img = element.selectFirst("img") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst("h3, h4, .title, p")?.text() 
                ?: element.attr("title").ifBlank { img.attr("alt") }

            if (mangaTitle.isBlank()) return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link)
                title = mangaTitle
                thumbnail_url = img.imgAttr()
            }
        }.distinctBy { it.url }

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

        urlBuilder.addPathSegment("")
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
        val mangas = document.select("div.cate-manga div.mainpage-manga, div.mainpage-manga").mapNotNull { element ->
            val linkElement = element.selectFirst("div.media-body a, h4 a, a[href*='/leer/']") ?: return@mapNotNull null
            SManga.create().apply {
                setUrlWithoutDomain(linkElement.attr("abs:href"))
                title = linkElement.text()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }

        val hasNextPage = document.selectFirst("ul.pagination > li.active + li") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Los filtros serán ignorados si se realiza una búsqueda por texto."),
        Filter.Header("Los filtros no se pueden combinar entre ellos."),
        GenreFilter(),
        AlphabeticFilter(),
        StatusFilter(),
    )

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".container a[href*='/leer/']").mapNotNull { element ->
            val link = element.attr("abs:href")
            val img = element.selectFirst("img") ?: return@mapNotNull null
            val mangaTitle = element.selectFirst("h3, h4, .media-heading")?.text()
                ?: element.attr("title").ifBlank { img.attr("alt") }

            if (mangaTitle.isBlank()) return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link)
                title = mangaTitle
                thumbnail_url = img.imgAttr()
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text() ?: ""

            val altNames = document.selectFirst(".description-update > span:contains(Títulos Alternativos:) + :matchText")?.text()
            val desc = document.selectFirst("#example2, .detail-content, .description")?.text()
            description = buildString {
                if (!desc.isNullOrEmpty()) append(desc)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alt name(s): ")
                    append(altNames)
                }
            }

            genre = document.select(".description-update a[href*='/genre/']").joinToString { it.text() }
            status = document.selectFirst(".description-update > span:contains(Estado:) + :matchText")?.text()?.toStatus() ?: SManga.UNKNOWN
            thumbnail_url = document.selectFirst(".cover-detail > img, .detail-info img")?.imgAttr()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterElements = document.select(".chapter-list ul li, .chapter-list div.row, ul.list-chapters li")
        
        return chapterElements.mapNotNull { element ->
            val link = element.selectFirst("a[href*='/leer/'], a.xanh, a") ?: return@mapNotNull null
            val chapterUrl = link.attr("abs:href")
            if (!chapterUrl.contains("/leer/")) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(chapterUrl)
                name = link.text().ifBlank { element.selectFirst(".chapter-title")?.text() ?: "Capítulo" }
            }
        }
    }

    private var cachedScriptUrl: String? = null

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // Intento 1: Extracción directa si las imágenes están renderizadas en el DOM
        val directImages = document.select("#array_data img, .chapter-content img, .reading-content img")
            .mapNotNull { it.imgAttr().takeIf { src -> src.startsWith("http") } }
        
        if (directImages.isNotEmpty()) {
            return directImages.mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
        }

        // Intento 2: Decodificación por script ofuscado con fallback seguro
        val orderList = document.selectFirst("meta[property=ad:check]")?.attr("content")
            ?.replace(ORDER_LIST_REGEX, "-")
            ?.split("-")

        val useReversedString = orderList?.any { it == "01" } == true

        val arrayDataElement = document.selectFirst("#array_data")
        val arrayData = arrayDataElement?.text() ?: arrayDataElement?.attr("value") ?: ""

        if (arrayData.isBlank()) {
            throw Exception("No se encontraron páginas en este capítulo")
        }

        val scripts = document.select("head > script[src*=.js], script[src^=/assets/]")
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
            val scriptData = runCatching {
                notRateLimitClient.newCall(GET(scriptUrl, headers)).execute().use { it.body.string() }
            }.getOrNull() ?: continue

            val deobfuscatedScript = runCatching { Deobfuscator.deobfuscateScript(scriptData) }.getOrNull()
            if (deobfuscatedScript != null && deobfuscatedScript.contains("#array_data")) {
                dataScript = deobfuscatedScript
                cachedScriptUrl = scriptUrl
                break
            }
        }

        if (dataScript == null) {
            // Si el script no coincide, intentar split directo si arrayData ya es Base64
            val directUrls = runCatching {
                String(Base64.decode(arrayData, Base64.DEFAULT), Charset.forName("UTF-8")).split(",")
            }.getOrNull()

            if (!directUrls.isNullOrEmpty()) {
                return directUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
            }
            throw Exception("No se pudo obtener el script de descifrado")
        }

        val keys = KEY_REGEX.findAll(dataScript).map { it.groupValues[1] }.toList()
        if (keys.size < 2) {
            throw Exception("Error analizando las claves de cifrado")
        }
        val (key1, key2) = keys

        val encodedUrls = arrayData.replace(DECODE_REGEX) {
            val index = key2.indexOf(it.value)
            if (index in key1.indices) key1[index].toString() else it.value
        }

        val urlList = String(Base64.decode(encodedUrls, Base64.DEFAULT), Charset.forName("UTF-8")).split(",")

        val sortedUrls = orderList?.mapNotNull {
            val idx = if (useReversedString) it.reversed().toIntOrNull() else it.toIntOrNull()
            if (idx != null && idx in urlList.indices) urlList[idx] else null
        }?.reversed()?.takeIf { it.isNotEmpty() } ?: urlList

        return sortedUrls.mapIndexed { i, imageUrl ->
            Page(i, imageUrl = imageUrl)
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("src") -> attr("abs:src")
        else -> ""
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
