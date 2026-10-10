package eu.kanade.tachiyomi.extension.es.kumanga

import android.util.Base64
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.nio.charset.Charset
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Kumanga :
    HttpSource(),
    ConfigurableSource {

    private val apiUrl = "https://www.kumanga.com/backend/ajax/searchengine2.php"

    override val supportsLatest = true

    private var rCookie: String? = null

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(4, 1.seconds)
        .addNetworkInterceptor { chain ->
            val req = chain.request()
            val builder = req.newBuilder()
            if (rCookie != null) {
                val existingCookie = req.header("Cookie")
                if (existingCookie != null) {
                    if (!existingCookie.contains(rCookie!!)) {
                        builder.header("Cookie", "$existingCookie; $rCookie")
                    }
                } else {
                    builder.header("Cookie", rCookie!!)
                }
            }
            val res = chain.proceed(builder.build())
            val setCookies = res.headers("Set-Cookie")
            val rCookieHeader = setCookies.find { it.startsWith("__r=") }
            if (rCookieHeader != null) {
                rCookie = rCookieHeader.substringBefore(";")
            }
            res
        }
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .setRandomUserAgent()

    override fun popularMangaRequest(page: Int): Request {
        val form = FormBody.Builder()
            .add("page", page.toString())
            .add("perPage", CONTENT_PER_PAGE.toString())
            .add("retrieveCategories", "true")
            .add("retrieveAuthors", "true")
            .add("contentType", "manga")
            .build()

        val apiHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
        return POST(apiUrl, apiHeaders, form)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.body.string().parseAs<KumangaSearchResponseDto>()
        val mangas = dto.contents.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.contents.size >= CONTENT_PER_PAGE)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.h_move").mapNotNull { element ->
            val linkManga = element.selectFirst("a.mhu-name") ?: return@mapNotNull null
            val linkChapter = element.selectFirst("a.mhu-card")

            SManga.create().apply {
                val rawHref = linkManga.attr("href")
                url = if (rawHref.startsWith("http")) {
                    rawHref.removePrefix(baseUrl)
                } else if (rawHref.startsWith("/")) {
                    rawHref
                } else {
                    "/$rawHref"
                }
                title = linkManga.text().trim()

                if (linkChapter != null) {
                    val bgImage = linkChapter.attr("style")
                    val bgUrl = bgImage.substringAfter("url(").substringBefore(")").trim('"', '\'')
                    if (bgUrl.isNotEmpty()) {
                        thumbnail_url = if (bgUrl.startsWith("http")) bgUrl else "$baseUrl/$bgUrl"
                    }
                }
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val form = FormBody.Builder()
            .add("page", page.toString())
            .add("perPage", CONTENT_PER_PAGE.toString())
            .add("retrieveCategories", "true")
            .add("retrieveAuthors", "true")
            .add("contentType", "manga")

        if (query.isNotBlank()) {
            form.add("keywords", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is TypeFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { form.add("type_filter[]", it.id) }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        form.add("status_filter[]", filter.toUriPart())
                    }
                }
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { form.add("category_filter[]", it.id) }
                }
                else -> {}
            }
        }

        val apiHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
        return POST(apiUrl, apiHeaders, form.build())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()?.trim()
                ?: document.selectFirst("meta[property='og:title']")?.attr("content")?.removeSuffix(" - KuManga")?.trim()
                ?: ""
            val mangaId = response.request.url.encodedPath.substringAfter("/manga/").substringBefore("/").toIntOrNull()
            thumbnail_url = mangaId?.let { "https://static.kumanga.com/manga/${(it / 2500) + 1}/$it.jpg" }
                ?: document.selectFirst("img.lazy-loaded, img[data-src*='/manga/'], img[src*='/manga/']")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                } ?: document.selectFirst("meta[property='og:image']")?.attr("content")
            description = document.selectFirst("#idesc, .Mdesc, .panel-body p, .description")?.let {
                it.select("br").prepend("\\n")
                it.text().replace("\\n", "\n").replace("\n ", "\n").trim()
            } ?: document.selectFirst("meta[property='og:description']")?.attr("content")?.trim()
            genre = document.select("a[href*='categories='], a[href*='genre=']")
                .joinToString { it.text().trim() }
            val bodyText = document.text()
            status = when {
                bodyText.contains("Activo", ignoreCase = true) -> SManga.ONGOING
                bodyText.contains("Finalizado", ignoreCase = true) -> SManga.COMPLETED
                bodyText.contains("Inconcluso", ignoreCase = true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterList = mutableListOf<SChapter>()
        val currentPath = response.request.url.encodedPath
        val mangaId = currentPath.substringAfter("/manga/").substringBefore("/")

        document.select("a[href*='/capitulo/'], a[href*='/manga/c/']").forEach { el ->
            val href = el.attr("href")
            val chNum = href.substringAfterLast("/").trim()
            val ch = SChapter.create().apply {
                url = "/manga/$mangaId/capitulo/$chNum"
                name = "Capítulo $chNum"
                chapter_number = chNum.toFloatOrNull() ?: -1f
            }
            chapterList.add(ch)
        }

        val scriptContent = document.selectFirst("script:containsData(OTHER_CHAPTERS)")?.data()
        if (scriptContent != null) {
            val jsonStr = scriptContent.substringAfter("let OTHER_CHAPTERS =").substringBefore(";").trim()
            runCatching {
                val otherChapters = jsonStr.parseAs<List<KumangaOtherChapterDto>>()
                otherChapters.forEach { item ->
                    val num = item.numCap?.content ?: return@forEach
                    val ch = SChapter.create().apply {
                        url = "/manga/$mangaId/capitulo/$num"
                        name = "Capítulo $num"
                        chapter_number = num.toFloatOrNull() ?: -1f
                    }
                    chapterList.add(ch)
                }
            }
        }

        return chapterList.distinctBy { it.url }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val raw = chapter.url
        val withSlash = if (raw.startsWith("/")) raw else "/$raw"
        val cleanUrl = if (withSlash.contains("/capitulo/")) {
            val mangaId = withSlash.substringAfter("/manga/").substringBefore("/")
            val capNum = withSlash.substringAfterLast("/").trim()
            "/manga/$mangaId/capitulo/$capNum"
        } else {
            withSlash
        }
        return GET("$baseUrl$cleanUrl", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        var document = response.asJsoup()
        val currentPath = response.request.url.encodedPath
        val mangaId = currentPath.substringAfter("/manga/").substringBefore("/")

        val chapterId = document.selectFirst("input[value*='/manga/c/']")?.attr("value")
            ?.substringAfterLast("/")?.trim()
            ?: document.selectFirst("a#leer[href], a[href*='manga/leer/'], a[href*='/leer/']")?.attr("href")
                ?.substringAfterLast("/")?.trim()
            ?: currentPath.substringAfterLast("/").trim()

        if (mangaId.isNotBlank() && chapterId.isNotBlank() && chapterId != mangaId) {
            val readerHeaders = headers.newBuilder()
                .set("Referer", "$baseUrl/manga/$mangaId/capitulo/${currentPath.substringAfterLast("/")}")
                .build()
            val readerReq = GET("$baseUrl/manga/leer/$chapterId", readerHeaders)
            val readerRes = client.newCall(readerReq).execute()
            if (!readerRes.isSuccessful) {
                throw Exception("HTTP ${readerRes.code}: Abre el WebView para resolver Cloudflare.")
            }
            document = readerRes.asJsoup()
        }

        val hexImages = document.select("img[data-src*='img.php?src='], img[src*='img.php?src=']")
        if (hexImages.isNotEmpty()) {
            val pages = mutableListOf<Page>()
            hexImages.forEachIndexed { index, el ->
                val src = el.attr("data-src").ifEmpty { el.attr("src") }
                val hex = src.substringAfter("img.php?src=").substringBefore("&")
                val decodedUrl = decodeHex(hex)
                if (decodedUrl.isNotBlank()) {
                    pages.add(Page(index, imageUrl = decodedUrl))
                }
            }
            if (pages.isNotEmpty()) return pages
        }

        val rawPUrl = document.selectFirst("script:containsData(pUrl=)")?.data()
            ?.substringAfter("pUrl=")
            ?.substringBefore(";")
            ?.trim()
            ?.removeSurrounding("'", "\"")

        if (!rawPUrl.isNullOrEmpty()) {
            val decoded = runCatching { decodeBase64(decodeBase64(rawPUrl).reversed().drop(10).dropLast(10)) }.getOrNull()
            if (decoded != null) {
                val imageList = runCatching { decoded.parseAs<List<KumangaImageDto>>() }.getOrNull()
                if (imageList != null && imageList.isNotEmpty()) {
                    return imageList.mapIndexedNotNull { index, dto ->
                        val imgUrl = dto.imgUrl ?: return@mapIndexedNotNull null
                        val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "$baseUrl/${imgUrl.removePrefix("/").replace("\\", "")}"
                        Page(index, imageUrl = fullUrl)
                    }
                }
            }
        }

        // New rpics XOR obfuscation
        val htmlStr = document.html()
        val rpicsVarMatch = Regex("""const\s+rpics\s*=\s*([a-zA-Z0-9_]+);""").find(htmlStr)
        if (rpicsVarMatch != null) {
            val varName = rpicsVarMatch.groupValues[1]
            val b64Match = Regex("""const\s+$varName\s*=\s*"([^"]+)";""").find(htmlStr)
            if (b64Match != null) {
                val b64Str = b64Match.groupValues[1]
                val key = "Jr54VwepF4La"
                val decodedBytes = runCatching { Base64.decode(b64Str, Base64.DEFAULT) }.getOrNull()
                if (decodedBytes != null) {
                    val decryptedJson = String(
                        decodedBytes.mapIndexed { i, byte ->
                            (byte.toInt() xor key[i % key.length].code).toByte()
                        }.toByteArray(),
                    )
                    val imageUrls = runCatching { decryptedJson.parseAs<List<String>>() }.getOrNull()
                    if (imageUrls != null && imageUrls.isNotEmpty()) {
                        return imageUrls.mapIndexed { index, imgUrl ->
                            val fullUrl = if (imgUrl.startsWith("http")) imgUrl else "$baseUrl/${imgUrl.removePrefix("/").replace("\\", "")}"
                            Page(index, imageUrl = fullUrl)
                        }
                    }
                }
            }
        }

        // Fallback for direct image links in the DOM that bypass img.php and pUrl
        val directImages = document.select("img").mapNotNull {
            val src = it.attr("data-src").ifEmpty { it.attr("src") }
            val cleanSrc = src.substringBefore("?")
            if (cleanSrc.contains("/manga/") &&
                !cleanSrc.contains("static.kumanga.com") &&
                cleanSrc.substringAfterLast(".").lowercase() in listOf("jpg", "jpeg", "png", "webp")
            ) {
                if (src.startsWith("http")) src else "$baseUrl$src"
            } else {
                null
            }
        }

        if (directImages.isNotEmpty()) {
            return directImages.mapIndexed { index, url -> Page(index, imageUrl = url) }
        }

        throw Exception("No se encontraron páginas. Abre el WebView (ícono del mundo) para resolver Cloudflare.")
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun decodeHex(hex: String): String = try {
        hex.chunked(2)
            .map { it.toInt(16).toChar() }
            .joinToString("")
    } catch (_: Exception) {
        ""
    }

    private fun decodeBase64(encoded: String): String = Base64.decode(encoded, Base64.DEFAULT).toString(Charset.forName("UTF-8"))

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    override fun getFilterList() = FilterList(
        TypeFilter(getTypeList()),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        GenreFilter(getGenreList()),
    )

    private class Type(name: String, val id: String) : Filter.CheckBox(name)
    private class TypeFilter(types: List<Type>) : Filter.Group<Type>("Tipo", types)

    private class StatusFilter :
        UriPartFilter(
            "Estado",
            arrayOf(
                Pair("Todos", ""),
                Pair("Activo", "1"),
                Pair("Finalizado", "2"),
                Pair("Inconcluso", "3"),
            ),
        )

    private class Genre(name: String, val id: String) : Filter.CheckBox(name)
    private class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Género", genres)

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private fun getTypeList() = listOf(
        Type("Manga", "1"),
        Type("Manhwa", "2"),
        Type("Manhua", "3"),
        Type("One shot", "4"),
        Type("Doujinshi", "5"),
    )

    private fun getGenreList() = listOf(
        Genre("Acción", "1"),
        Genre("Artes marciales", "2"),
        Genre("Automóviles", "3"),
        Genre("Aventura", "4"),
        Genre("Boys Love", "49"),
        Genre("Ciencia Ficción", "5"),
        Genre("Comedia", "6"),
        Genre("Demonios", "7"),
        Genre("Deportes", "8"),
        Genre("Doujinshi", "9"),
        Genre("Drama", "10"),
        Genre("Ecchi", "11"),
        Genre("Espacio exterior", "12"),
        Genre("Fantasía", "13"),
        Genre("Gender bender", "14"),
        Genre("Girls Love", "50"),
        Genre("Gore", "46"),
        Genre("Harem", "15"),
        Genre("Hentai", "16"),
        Genre("Histórico", "17"),
        Genre("Horror", "18"),
        Genre("Isekai", "51"),
        Genre("Josei", "19"),
        Genre("Juegos", "20"),
        Genre("Locura", "21"),
        Genre("Magia", "22"),
        Genre("Mecha", "23"),
        Genre("Militar", "24"),
        Genre("Misterio", "25"),
        Genre("Música", "26"),
        Genre("Niños", "27"),
        Genre("Parodia", "28"),
        Genre("Policía", "29"),
        Genre("Psicológico", "30"),
        Genre("Recuentos de la vida", "31"),
        Genre("Reencarnación", "52"),
        Genre("Romance", "32"),
        Genre("Samurai", "33"),
        Genre("Seinen", "34"),
        Genre("Shoujo", "35"),
        Genre("Shoujo Ai", "36"),
        Genre("Shounen", "37"),
        Genre("Shounen Ai", "38"),
        Genre("Sobrenatural", "39"),
        Genre("Súperpoderes", "41"),
        Genre("Supervivencia", "53"),
        Genre("Suspenso", "40"),
        Genre("Terror", "47"),
        Genre("Tragedia", "48"),
        Genre("Vampiros", "42"),
        Genre("Vida escolar", "43"),
        Genre("Yaoi", "44"),
        Genre("Yuri", "45"),
    )

    companion object {
        private const val CONTENT_PER_PAGE = 24
    }
}
