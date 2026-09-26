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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class LeerCapitulo : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(1, 3.seconds) { it.host == baseUrlHost }
        .build()

    private val notRateLimitClient = network.client
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "az")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/manga/".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "za")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = "$baseUrl/manga/".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("q", query)
        }

        filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("genre", it.toUriPart())
        }
        filters.firstInstanceOrNull<StatusFilter>()?.takeIf { it.state != 0 }?.let {
            urlBuilder.addQueryParameter("status", it.toUriPart())
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("article.lc-card").mapNotNull { card ->
            val link = card.selectFirst("a.lc-card-name") ?: card.selectFirst("a.lc-card-cover") ?: return@mapNotNull null
            val url = link.attr("abs:href")
            if (url.isBlank()) return@mapNotNull null

            val titleText = card.selectFirst("a.lc-card-name")?.text() ?: link.text()
            if (titleText.isBlank()) return@mapNotNull null

            val img = card.selectFirst("a.lc-card-cover img")

            SManga.create().apply {
                setUrlWithoutDomain(url)
                title = titleText.trim()
                thumbnail_url = img?.imgAttr()
            }
        }.distinctBy { it.url }

        val hasNextPage = document.selectFirst("ul.pagination li.active + li:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("article h1, h1")?.text()?.trim() ?: ""

            val altNames = document.selectFirst("article p.lc-muted")?.text()?.trim()
            val desc = document.selectFirst("#sinopsis p, #sinopsis")?.text()?.trim()
            description = buildString {
                if (!desc.isNullOrEmpty()) append(desc)
                if (!altNames.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Alt name(s): ")
                    append(altNames)
                }
            }

            genre = document.select("article .badge").joinToString { it.text().trim() }

            val facts = document.select("ul.lc-facts li")
            author = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Autor", true) == true }
                ?.selectFirst("span:not(.k)")?.text()?.trim()
            artist = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Dibujo", true) == true }
                ?.selectFirst("span:not(.k)")?.text()?.trim()

            val statusText = facts.firstOrNull { it.selectFirst("span.k")?.text()?.contains("Estado", true) == true }
                ?.selectFirst("a, span:not(.k)")?.text()
            status = statusText?.toStatus() ?: SManga.UNKNOWN

            thumbnail_url = document.selectFirst(".lc-cover-lg img, article img")?.imgAttr()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterRows = document.select("#chapterList a.lc-chapter-row")

        return chapterRows.mapNotNull { element ->
            val url = element.attr("abs:href")
            if (url.isBlank()) return@mapNotNull null

            val nameText = element.selectFirst("span.n")?.text() ?: element.text()
            val dateText = element.selectFirst("span.d")?.text()

            SChapter.create().apply {
                setUrlWithoutDomain(url)
                name = nameText.trim()
                date_upload = dateText?.let {
                    runCatching { dateFormat.parse(it)?.time }.getOrNull()
                } ?: 0L
            }
        }
    }

    private var cachedScriptUrl: String? = null

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val directImages = document.select("#chapter-content img, .chapter-content img, #array_data img")
            .mapNotNull { it.imgAttr().takeIf { src -> src.startsWith("http") } }

        if (directImages.isNotEmpty() && directImages.size > 2) {
            return directImages.mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
        }

        val arrayDataElement = document.selectFirst("#array_data") ?: document.select("input[type=hidden]").firstOrNull { it.attr("value").length > 100 }
        val arrayData = arrayDataElement?.text() ?: arrayDataElement?.attr("value") ?: ""

        if (arrayData.isBlank()) throw Exception("Las imágenes no están expuestas en el HTML del visor.")

        val orderList = document.selectFirst("meta[property=ad:check]")?.attr("content")?.replace(ORDER_LIST_REGEX, "-")?.split("-")
        val useReversedString = orderList?.any { it == "01" } == true

        val scripts = document.select("head > script[src*=.js], script[src^=/assets/]").map { it.attr("abs:src") }.reversed().toMutableList()

        var dataScript: String? = null
        cachedScriptUrl?.let { if (scripts.remove(it)) scripts.add(0, it) }

        for (scriptUrl in scripts) {
            val scriptData = runCatching { notRateLimitClient.newCall(GET(scriptUrl, headers)).execute().use { it.body.string() } }.getOrNull() ?: continue
            val deobfuscatedScript = runCatching { Deobfuscator.deobfuscateScript(scriptData) }.getOrNull()
            if (deobfuscatedScript != null && deobfuscatedScript.contains("#array_data")) {
                dataScript = deobfuscatedScript
                cachedScriptUrl = scriptUrl
                break
            }
        }

        if (dataScript == null) {
            val directUrls = runCatching { String(Base64.decode(arrayData, Base64.DEFAULT), Charset.forName("UTF-8")).split(",") }.getOrNull()
            if (!directUrls.isNullOrEmpty()) return directUrls.mapIndexed { i, url -> Page(i, imageUrl = url) }
            throw Exception("No se pudo descifrar el script de imágenes.")
        }

        val keys = KEY_REGEX.findAll(dataScript).map { it.groupValues[1] }.toList()
        if (keys.size < 2) throw Exception("Error de claves de cifrado")

        val encodedUrls = arrayData.replace(DECODE_REGEX) {
            val index = keys[1].indexOf(it.value)
            if (index in keys[0].indices) keys[0][index].toString() else it.value
        }

        val urlList = String(Base64.decode(encodedUrls, Base64.DEFAULT), Charset.forName("UTF-8")).split(",")
        val sortedUrls = orderList?.mapNotNull {
            val idx = if (useReversedString) it.reversed().toIntOrNull() else it.toIntOrNull()
            if (idx != null && idx in urlList.indices) urlList[idx] else null
        }?.reversed()?.takeIf { it.isNotEmpty() } ?: urlList

        return sortedUrls.mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("src") -> attr("abs:src")
        else -> ""
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.toStatus() = when (this.lowercase().trim()) {
        "ongoing", "en emision" -> SManga.ONGOING
        "completed", "finalizado" -> SManga.COMPLETED
        "paused", "pausado" -> SManga.ON_HIATUS
        "cancelled", "cancelado" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter(),
    )

    companion object {
        private val ORDER_LIST_REGEX = "[^\\d]+".toRegex()
        private val KEY_REGEX = """'([A-Z0-9]{62})'""".toRegex(RegexOption.IGNORE_CASE)
        private val DECODE_REGEX = Regex("[A-Z0-9]", RegexOption.IGNORE_CASE)
    }
}
