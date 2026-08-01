package eu.kanade.tachiyomi.extension.es.ritto

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import rx.Observable
import java.io.ByteArrayOutputStream
import java.io.File

@Source
abstract class Ritto : HttpSource() {

    override val supportsLatest = true

    override val client by lazy {
        super.client.newBuilder()
            .addInterceptor(::pdfPageInterceptor)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/catalogo?busqueda=&tipo=&estado=&genero=&categoria=&orden=vistas&pagina=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = catalogParse(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/catalogo?busqueda=&tipo=&estado=&genero=&categoria=&orden=reciente&pagina=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = catalogParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val tipo = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue ?: ""
        val estado = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue ?: ""
        val genero = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue ?: ""
        val categoria = filters.firstInstanceOrNull<CategoryFilter>()?.selectedValue ?: ""
        val orden = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: "reciente"

        val url = "$baseUrl/catalogo".toHttpUrl().newBuilder()
            .addQueryParameter("busqueda", query)
            .addQueryParameter("tipo", tipo)
            .addQueryParameter("estado", estado)
            .addQueryParameter("genero", genero)
            .addQueryParameter("categoria", categoria)
            .addQueryParameter("orden", orden)
            .addQueryParameter("pagina", page.toString())
            .build()
            .toString()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map { catalogParse(it) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/obra/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")?.text()
                ?: throw Exception("Título no encontrado")
            description = document.selectFirst("p.ref-obra-description")?.text()
            thumbnail_url = document.selectFirst("img[src*=\"/covers/\"]")?.attr("abs:src")

            val genreLinks = document.select("a[href*=\"/catalogo?genero=\"]")
            genre = genreLinks.map { it.text() }.distinct().joinToString(", ")

            val statusLink = document.selectFirst("a[href*=\"/catalogo?estado=\"]")
            val statusText = statusLink?.attr("href") ?: ""
            status = when {
                statusText.contains("EN_EMISION") -> SManga.ONGOING
                statusText.contains("FINALIZADO") -> SManga.COMPLETED
                statusText.contains("PAUSADO") -> SManga.ON_HIATUS
                statusText.contains("CANCELADO") -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            author = document.selectFirst("a[href*=\"/autor/\"]")?.text()
        }
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/obra/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val chapters = parseChaptersFromRsc(body)

        if (chapters.isNotEmpty()) return chapters

        // Fallback to HTML parsing if RSC data not found
        val document = org.jsoup.Jsoup.parse(body, response.request.url.toString())
        return parseChaptersFromHtml(document)
    }

    private fun parseChaptersFromRsc(body: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()

        // Find the "items" array in RSC data (escaped as \"items\":[)
        val itemsStart = body.indexOf("\\\"items\\\":[")
        if (itemsStart == -1) return emptyList()

        // Extract the array by counting brackets
        val arrayStart = body.indexOf('[', itemsStart)
        var depth = 0
        var arrayEnd = arrayStart
        for (i in arrayStart until body.length) {
            when (body[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        arrayEnd = i
                        break
                    }
                }
            }
        }

        if (arrayEnd <= arrayStart) return emptyList()

        val rawArray = body.substring(arrayStart, arrayEnd + 1)

        // Unescape RSC format: \" -> "
        val cleanArray = rawArray.replace("\\\"", "\"").replace("\\\\", "\\")

        // Extract each chapter object
        val chapterRegex = Regex(
            """\{"id":"[^"]+","nombre":"((?:Cap|Vol)\.?[^"]*)".*?"href":"([^"]+)".*?"fechaLabel":"([^"]+)".*?"numero":(\d+(?:\.\d+)?)""",
        )
        val tituloExtraRegex = Regex(""""tituloExtra":"([^"]+)"""")

        for (match in chapterRegex.findAll(cleanArray)) {
            val nombre = match.groupValues[1]
            val href = match.groupValues[2]
            val fechaLabel = match.groupValues[3]
            val numero = match.groupValues[4].toFloatOrNull() ?: continue

            val tituloExtra = tituloExtraRegex
                .find(match.value)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            val displayName = if (tituloExtra != null) "$nombre · $tituloExtra" else nombre

            chapters.add(
                SChapter.create().apply {
                    url = href
                    name = displayName
                    chapter_number = numero
                    date_upload = parseDate(fechaLabel)
                },
            )
        }

        return chapters.sortedByDescending { it.chapter_number }
    }

    private fun parseChaptersFromHtml(document: Document): List<SChapter> {
        val chapters = document.select("article.obra-chapter-row")

        return chapters.map { row ->
            SChapter.create().apply {
                val link = row.selectFirst("a[href*=\"/capitulo/\"]")
                url = link?.attr("href") ?: ""
                name = link?.text()?.trim() ?: ""

                val numberText = row.selectFirst(".obra-chapter-number")?.text()?.trim()
                chapter_number = numberText?.toFloatOrNull() ?: -1f

                val spans = row.select("span")
                for (span in spans) {
                    val text = span.text().trim()
                    if (text.matches("\\d{1,2}\\s+\\w{3}\\s+\\d{4}".toRegex())) {
                        date_upload = parseDate(text)
                        break
                    }
                }
            }
        }.sortedByDescending { it.chapter_number }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()

        // Check if this is a PDF/document chapter (novel)
        val archivoMatch = Regex(""""archivoUrl\\?":\\?"([^"]+)"""").find(body)
            ?: Regex("""archivoUrl[^"]*"[^"]*"([^"]+/archivo)""").find(body)

        if (archivoMatch != null || body.contains("/api/capitulos/")) {
            return handlePdfChapter(body, response)
        }

        // Try RSC data first (servidores[].imagenes[].url)
        val rscPages = parsePagesFromRsc(body)
        if (rscPages.isNotEmpty()) return rscPages

        // Fallback to HTML (img.reader-image)
        val document = org.jsoup.Jsoup.parse(body, response.request.url.toString())
        return parsePagesFromHtml(document)
    }

    private fun handlePdfChapter(body: String, response: Response): List<Page> {
        // Extract archivoUrl from RSC data
        val urlMatch = Regex("""\\?"archivoUrl\\?":\\?"([^"]+)\\?"""").find(body)
            ?: throw Exception("No se pudo encontrar la URL del archivo PDF")

        val archivoUrl = urlMatch.groupValues[1].replace("\\/", "/")
        val fullUrl = if (archivoUrl.startsWith("http")) archivoUrl else "$baseUrl$archivoUrl"

        // Download PDF to cache
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "ritto_pdf")
        cacheDir.mkdirs()
        val fileHash = fullUrl.hashCode().toString()
        val pdfFile = File(cacheDir, "chapter_$fileHash.pdf")

        if (!pdfFile.exists()) {
            val pdfRequest = GET(fullUrl, headers)
            client.newCall(pdfRequest).execute().use { pdfResponse ->
                if (!pdfResponse.isSuccessful) throw Exception("Error al descargar PDF: ${pdfResponse.code}")
                pdfFile.writeBytes(pdfResponse.body.bytes())
            }
        }

        // Get page count using PdfRenderer
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pageCount = renderer.pageCount
        renderer.close()
        pfd.close()

        if (pageCount == 0) throw Exception("El PDF no tiene páginas")

        // Return pages with special pdf:// URLs
        return (0 until pageCount).map { index ->
            Page(index, imageUrl = "$PDF_PREFIX$fileHash|$index")
        }
    }

    private fun parsePagesFromRsc(body: String): List<Page> {
        // Find "imagenes" array in RSC data (escaped as \"imagenes\":[)
        val imagenesStart = body.indexOf("\\\"imagenes\\\":[")
        if (imagenesStart == -1) return emptyList()

        val arrayStart = body.indexOf('[', imagenesStart)
        var depth = 0
        var arrayEnd = arrayStart
        for (i in arrayStart until body.length) {
            when (body[i]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        arrayEnd = i
                        break
                    }
                }
            }
        }

        if (arrayEnd <= arrayStart) return emptyList()

        val rawArray = body.substring(arrayStart, arrayEnd + 1)
        val cleanArray = rawArray.replace("\\\"", "\"").replace("\\\\", "\\")

        val urlRegex = Regex(""""url":"([^"]+)"""")
        return urlRegex.findAll(cleanArray).mapIndexed { index, match ->
            val relativeUrl = match.groupValues[1]
            val imageUrl = "$CDN_BASE_URL/$relativeUrl"
            Page(index, imageUrl = imageUrl)
        }.toList()
    }

    private fun parsePagesFromHtml(document: Document): List<Page> = document.select("img.reader-image").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("abs:src"))
    }

    override fun imageRequest(page: Page): Request {
        if (page.imageUrl!!.startsWith(PDF_PREFIX)) {
            val data = page.imageUrl!!.removePrefix(PDF_PREFIX)
            val parts = data.split("|")
            return GET("$baseUrl/__pdf__/${parts[0]}/${parts[1]}", headers)
        }
        return GET(page.imageUrl!!, headers)
    }

    private fun pdfPageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url

        if (url.encodedPath.startsWith("/__pdf__/")) {
            val segments = url.pathSegments
            val fileHash = segments[1]
            val pageIndex = segments[2].toInt()

            // Find the cached PDF file
            val cacheDir = File(System.getProperty("java.io.tmpdir"), "ritto_pdf")
            val pdfFile = cacheDir.listFiles()?.find { it.name.contains(fileHash) }
                ?: throw Exception("Archivo PDF no encontrado en caché")

            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val page = renderer.openPage(pageIndex)

            val scale = 2f
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888,
            )
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()

            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()

            val imageBytes = output.toByteArray()
            return Response.Builder()
                .request(request)
                .protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(imageBytes.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }

        return chain.proceed(request)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private const val CDN_BASE_URL = "https://cdn.solitarionf.one"
        private const val PDF_PREFIX = "pdf://"
    }

    // ============================== Helpers ==============================

    private fun catalogParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaCards = document.select("a[href*=\"/obra/\"]")

        val mangas = mangaCards.mapNotNull { card ->
            val href = card.attr("href")
            if (!href.startsWith("/obra/")) return@mapNotNull null

            val img = card.selectFirst("img[src*=\"cdn.solitarionf.one\"]") ?: return@mapNotNull null
            val h3 = card.selectFirst("h3") ?: return@mapNotNull null

            SManga.create().apply {
                url = href.removePrefix("/obra/")
                title = h3.text()
                thumbnail_url = img.attr("abs:src")
            }
        }

        // Check if there's a next page by looking at pagination buttons
        val pageButtons = document.select("button.h-8.w-8")
        val activeIdx = pageButtons.indexOfFirst {
            it.className().contains("bg-[#D93025]")
        }
        val hasNextPage = if (pageButtons.isNotEmpty() && activeIdx >= 0) {
            activeIdx < pageButtons.size - 1
        } else {
            mangas.size >= 20
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseDate(dateStr: String): Long = try {
        val months = mapOf(
            "ene" to 0, "feb" to 1, "mar" to 2, "abr" to 3, "may" to 4, "jun" to 5,
            "jul" to 6, "ago" to 7, "sep" to 8, "oct" to 9, "nov" to 10, "dic" to 11,
        )
        val parts = dateStr.split(" ")
        if (parts.size == 3) {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_MONTH, parts[0].toInt())
            cal.set(java.util.Calendar.MONTH, months[parts[1].lowercase()] ?: 0)
            cal.set(java.util.Calendar.YEAR, parts[2].toInt())
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } else {
            0L
        }
    } catch (_: Exception) {
        0L
    }

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
        CategoryFilter(),
        SortFilter(),
    )
}
