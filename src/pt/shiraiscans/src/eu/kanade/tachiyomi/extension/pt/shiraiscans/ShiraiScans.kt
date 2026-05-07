package eu.kanade.tachiyomi.extension.pt.shiraiscans

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class ShiraiScans : HttpSource() {

    override val name = "Shirai Scans"

    override val baseUrl = "https://shiraixis.space"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            // 1. Handle image format fallbacks
            if (url.endsWith(IMAGE_SUFFIX)) {
                val imageBaseUrl = url.removeSuffix(IMAGE_SUFFIX)
                var lastResponse: Response? = null

                for (ext in EXTENSIONS_FALLBACK) {
                    val newUrl = imageBaseUrl + ext
                    val newRequest = request.newBuilder().url(newUrl).build()

                    try {
                        val response = chain.proceed(newRequest)
                        if (response.isSuccessful) {
                            return@addInterceptor response
                        }
                        lastResponse?.close()
                        lastResponse = response
                    } catch (e: Exception) {
                        lastResponse?.close()
                        lastResponse = null
                        if (ext == EXTENSIONS_FALLBACK.last()) throw e
                    }
                }
                return@addInterceptor lastResponse ?: throw IOException("Failed to fetch image")
            }

            // 2. Decode the Base64 Obfuscated HTML
            val response = chain.proceed(request)
            val contentType = response.body.contentType()

            if (contentType?.type == "text" && contentType.subtype.contains("html")) {
                val html = response.body.string()
                val match = B64_REGEX.find(html)

                if (match != null) {
                    val b64Str = match.groupValues[1]
                    val decodedHtml = try {
                        String(Base64.decode(b64Str, Base64.DEFAULT))
                    } catch (e: Exception) {
                        html
                    }
                    val newBody = decodedHtml.toResponseBody(contentType)
                    return@addInterceptor response.newBuilder().body(newBody).build()
                }

                val newBody = html.toResponseBody(contentType)
                return@addInterceptor response.newBuilder().body(newBody).build()
            }

            response
        }
        .build()

    private val dateFormat by lazy {
        SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 15
        return GET("$baseUrl/biblioteca.php?ajax=true&genero=todos&q=&offset=$offset", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val cards = document.select(".library-card")

        val mangas = cards.map {
            SManga.create().apply {
                val onClick = it.attr("onclick")
                url = "/" + onClick.substringAfter("href='").substringBefore("'")
                title = it.selectFirst(".library-title")!!.text()
                thumbnail_url = it.selectFirst(".library-cover")?.absUrl("src")
            }
        }

        return MangasPage(mangas, cards.size == 15)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("section.atualizacoes .manga-card, section#secao-atualizacoes .manga-card").map {
            SManga.create().apply {
                val onClick = it.attr("onclick")
                url = "/" + onClick.substringAfter("href='").substringBefore("'")
                title = it.selectFirst(".manga-title")!!.text()
                thumbnail_url = it.selectFirst(".manga-cover")?.absUrl("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: "todos"
        val offset = (page - 1) * 15

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("biblioteca.php")
            addQueryParameter("ajax", "true")
            addQueryParameter("genero", genre)
            addQueryParameter("q", query)
            addQueryParameter("offset", offset.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".obra-titulo")!!.text()
            thumbnail_url = document.selectFirst(".obra-capa-grande")?.absUrl("src")
            author = document.selectFirst(".info-linha:contains(Autor) span:last-child")?.text()?.takeIf { it != "?" }
            artist = document.selectFirst(".info-linha:contains(Artista) span:last-child")?.text()?.takeIf { it != "?" }
            description = document.selectFirst(".obra-sinopse")?.text()
            genre = document.select(".obra-generos .genero-badge").joinToString { it.text().removePrefix("#") }

            val statusText = document.selectFirst(".info-linha:contains(Status) span:last-child")?.text()
            status = when {
                statusText == null -> SManga.UNKNOWN
                statusText.contains("Lançamento", true) -> SManga.ONGOING
                statusText.contains("Completo", true) -> SManga.COMPLETED
                statusText.contains("Hiato", true) -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".lista-capitulos .capitulo-item").map {
            SChapter.create().apply {
                url = "/" + it.attr("href")
                name = it.selectFirst(".capitulo-title")!!.text().replace("NOVO", "").trim()
                date_upload = it.selectFirst(".capitulo-date")?.text()?.let { dateStr ->
                    dateFormat.tryParse(dateStr)
                } ?: 0L
            }
        }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(pagesData)")?.data()
            ?: throw Exception("Script com dados das páginas não encontrado")

        val jsonString = script.substringAfter("const pagesData = ").substringBefore(";")
        val pages = jsonString.parseAs<List<Dto>>()

        return pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.urlBase + IMAGE_SUFFIX)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        GenreFilter(),
    )

    companion object {
        private const val IMAGE_SUFFIX = ".shirai"
        private val EXTENSIONS_FALLBACK = listOf(".webp", ".jpg", ".png")
        private val B64_REGEX = """var\s+b64\s*=\s*['"]([A-Za-z0-9+/=\s]+)['"]""".toRegex()
    }
}
