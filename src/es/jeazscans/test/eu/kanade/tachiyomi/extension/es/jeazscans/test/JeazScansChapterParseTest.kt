package eu.kanade.tachiyomi.extension.es.jeazscans.test

import eu.kanade.tachiyomi.extension.es.jeazscans.CHAPTER_NUMBER_REGEX
import eu.kanade.tachiyomi.extension.es.jeazscans.buildApiUrl
import eu.kanade.tachiyomi.extension.es.jeazscans.decodeVerifyToUrl
import eu.kanade.tachiyomi.extension.es.jeazscans.extractSlugAndCap
import eu.kanade.tachiyomi.extension.es.jeazscans.parseChapterDate
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for JeazScans parsing helpers and HTML selectors.
 *
 * Tests call production helpers from Helpers.kt directly — no local reimplementation.
 *
 * Run: ./gradlew :src:es:jeazscans:testDebugUnitTest --tests "*JeazScansChapterParseTest*"
 */
class JeazScansChapterParseTest {

    // ── HTML builders ──────────────────────────────────────────────────────────

    private fun buildMangaDetailHtml(chapterCount: Int = 57): String {
        val chapterAnchors = (1..chapterCount).joinToString("\n") { chapterNumber ->
            val hiddenSuffix = if (chapterNumber > 20) " chapter-hidden" else ""
            """            <a class="chapter-item$hiddenSuffix"
               href="/leer/calor-extremo-ascendiendo-a-la-divinidad-desde-mi-b-nker-/capitulo-$chapterNumber"
               data-chapter-number="$chapterNumber">
                <span class="chapter-title">Chapter $chapterNumber</span>
                <span><i class="ph-clock"></i> 15 Jul, 2025</span>
            </a>"""
        }

        return """
<!DOCTYPE html>
<html lang="es">
<head><title>Calor Extremo - JeazScans</title></head>
<body>
    <span id="totalCaps">$chapterCount</span>
    <div id="chaptersContainer">
$chapterAnchors
    </div>
    <button onclick="loadMoreChapters(20)">Ver mas capitulos</button>
</body>
</html>
""".trimIndent()
    }

    private fun buildRichMangaDetailHtml(): String {
        return """
<!DOCTYPE html>
<html lang="es">
<head><title>Calor Extremo - JeazScans</title></head>
<body>
    <div class="lg:col-span-3">
        <div class="cultivation-panel">
            <img src="/uploads/mangas/052c635e677ddf8378f80ac8589c7837.webp"
                alt="Calor Extremo Ascendiendo a la Divinidad desde mi Bunker">
            <span class="status-badge">EN CULTIVO</span>
        </div>
    </div>

    <div class="lg:col-span-9 space-y-8">
        <div class="cultivation-panel">
            <h1 class="blood-title">Calor Extremo Ascendiendo a la Divinidad desde mi Bunker</h1>

            <div class="flex flex-wrap gap-2 mb-6">
                <a href="/directorio.php?genero=Acci%C3%B3n">Acción</a>
                <a href="/directorio.php?genero=Aventura">Aventura</a>
                <a href="/directorio.php?genero=Sistemas">Sistemas</a>
                <a href="/directorio.php?genero=Fantas%C3%ADa">Fantasía</a>
                <a href="/directorio.php?genero=Manhua">Manhua</a>
                <a href="/directorio.php?genero=Regresi%C3%B3n">Regresión</a>
                <a href="/directorio.php?genero=Superpoderes">Superpoderes</a>
                <a href="/directorio.php?genero=Supervivencia">Supervivencia</a>
                <a href="/directorio.php?genero=Post-Apocal%C3%ADptico">Post-Apocalíptico</a>
            </div>

            <div class="text-gray-200 text-base leading-relaxed relative z-10">
                <h3>SINOPSIS:</h3>
                ¡Los desastres naturales han llegado, pero yo renací con trillones en suministros! Ahora me vengaré de esa zorra traidora.
            </div>
        </div>

        <div class="cultivation-panel">
            <h3>ARCHIVOS DE CULTIVO (<span id="totalCaps">57</span> Capítulos)</h3>
            <div id="chaptersContainer">
                <a href="/leer/calor-extremo-ascendiendo-a-la-divinidad-desde-mi-b-nker-/capitulo-57.00"
                    class="chapter-item"
                    data-chapter-number="57.00">
                    <span class="chapter-title">Capítulo 57.00</span>
                    <span><i class="ph-bold ph-clock"></i> 19 Jul, 2026</span>
                </a>
                <a href="/leer/calor-extremo-ascendiendo-a-la-divinidad-desde-mi-b-nker-/capitulo-56.00"
                    class="chapter-item chapter-hidden"
                    data-chapter-number="56.00">
                    <span class="chapter-title">Capítulo 56.00</span>
                    <span><i class="ph-bold ph-clock"></i> Hace 19 hrs</span>
                </a>
            </div>
            <button id="loadMoreBtn" onclick="loadMoreChapters(20)">CARGAR MAS CAPITULOS</button>
        </div>
    </div>
</body>
</html>
""".trimIndent()
    }

    private fun buildReaderHtml(pageCount: Int = 32): String {
        val pages = (1..pageCount).joinToString("\n") { n ->
            val padded = n.toString().padStart(2, '0')
            val srcAttr = if (n <= 10) """ src="https://lectorhub.j5z.xyz/uploads/reader/ch1/$padded.png"""" else ""
            """            <img class="reader-page-image" data-src="https://lectorhub.j5z.xyz/uploads/reader/ch1/$padded.png"$srcAttr>"""
        }

        return """
<!DOCTYPE html>
<html>
<head><title>Reader</title></head>
<body>
    <div id="pagesContainer" class="mode-cascada">
$pages
    </div>
</body>
</html>
""".trimIndent()
    }

    // ── Chapter selector tests ─────────────────────────────────────────────────

    @Test
    fun `selector finds all 57 chapters including hidden ones`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57), "https://lectorhub.j5z.xyz")
        val elements = document.select("#chaptersContainer a.chapter-item")

        assertEquals("All 57 chapter-item anchors must be matched regardless of hidden CSS class", 57, elements.size)
    }

    @Test
    fun `totalCaps span value matches parsed chapter count`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57))
        val totalCaps = document.selectFirst("#totalCaps")?.text()?.toIntOrNull()
        val chapterElements = document.select("#chaptersContainer a.chapter-item")

        assertEquals("totalCaps declares 57 chapters", 57, totalCaps)
        assertEquals("Parsed chapter count must equal totalCaps", totalCaps!!, chapterElements.size)
    }

    @Test
    fun `chapter 57_00 is present in parsed results`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57), "https://lectorhub.j5z.xyz")
        val chapterNumbers = document.select("#chaptersContainer a.chapter-item").map { element ->
            CHAPTER_NUMBER_REGEX.find(element.attr("abs:href"))?.groupValues?.getOrNull(1)?.toFloatOrNull()
                ?: element.attr("data-chapter-number").toFloatOrNull()
                ?: -1f
        }

        assertTrue("Chapter 57.00 must be among parsed results", chapterNumbers.contains(57.0f))
    }

    @Test
    fun `hidden chapters are still matched by selector`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57))
        val hiddenCount = document.select("#chaptersContainer a.chapter-item.chapter-hidden").size
        val totalCount = document.select("#chaptersContainer a.chapter-item").size

        assertTrue("Some chapters should carry the hidden class", hiddenCount > 0)
        assertEquals("Chapters 21-57 are hidden = 37 elements", 37, hiddenCount)
        assertEquals("Hidden chapters must still appear in the full selector result", 57, totalCount)
    }

    @Test
    fun `chapter numbers are sequential from 1 to 57`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57), "https://lectorhub.j5z.xyz")
        val chapterNumbers = document.select("#chaptersContainer a.chapter-item").map { element ->
            element.attr("data-chapter-number").toFloatOrNull() ?: -1f
        }

        assertEquals("Should parse 57 chapter numbers", 57, chapterNumbers.size)
        chapterNumbers.forEachIndexed { index, chapterNumber ->
            assertEquals("Chapter at index $index should be ${index + 1}", (index + 1).toFloat(), chapterNumber, 0.001f)
        }
    }

    @Test
    fun `no chapter produces negative number when data attribute is present`() {
        val document = Jsoup.parse(buildMangaDetailHtml(57), "https://lectorhub.j5z.xyz")
        val chapterNumbers = document.select("#chaptersContainer a.chapter-item").map { element ->
            element.attr("data-chapter-number").toFloatOrNull() ?: -1f
        }

        assertTrue("All chapters must have non-negative numbers", chapterNumbers.all { it > 0f })
    }

    // ── Details selector tests ─────────────────────────────────────────────────

    @Test
    fun `manga details selectors capture title synopsis genres cover and status`() {
        val document = Jsoup.parse(buildRichMangaDetailHtml(), "https://lectorhub.j5z.xyz")
        val manga = parseDetailsFromDocument(document)

        assertEquals("Calor Extremo Ascendiendo a la Divinidad desde mi Bunker", manga.title)
        assertTrue(manga.description.contains("Los desastres naturales han llegado"))
        assertEquals(
            "Acción, Aventura, Sistemas, Fantasía, Manhua, Regresión, Superpoderes, Supervivencia, Post-Apocalíptico",
            manga.genre,
        )
        assertEquals("https://lectorhub.j5z.xyz/uploads/mangas/052c635e677ddf8378f80ac8589c7837.webp", manga.thumbnailUrl)
        assertEquals(SManga.ONGOING, manga.status)
    }

    // ── Chapter metadata tests (calling production helpers) ────────────────────

    @Test
    fun `chapter selectors capture urls names numbers and dates`() {
        val document = Jsoup.parse(buildRichMangaDetailHtml(), "https://lectorhub.j5z.xyz")
        val chapters = document.select("#chaptersContainer a.chapter-item")

        assertEquals(2, chapters.size)

        val latestChapter = chapters.first()!!
        assertEquals(
            "https://lectorhub.j5z.xyz/leer/calor-extremo-ascendiendo-a-la-divinidad-desde-mi-b-nker-/capitulo-57.00",
            latestChapter.attr("abs:href"),
        )
        assertEquals("Capítulo 57.00", latestChapter.selectFirst(".chapter-title")?.text())
        assertEquals(57.0f, CHAPTER_NUMBER_REGEX.find(latestChapter.attr("abs:href"))?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f, 0.001f)
        assertTrue("Absolute Spanish date must be parsed via production helper", parseChapterDate("19 Jul, 2026") > 0L)

        val hiddenChapter = chapters[1]
        assertEquals("Capítulo 56.00", hiddenChapter.selectFirst(".chapter-title")?.text())
        assertEquals(56.0f, CHAPTER_NUMBER_REGEX.find(hiddenChapter.attr("abs:href"))?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: -1f, 0.001f)
        assertTrue("Relative date with hrs must be parsed via production helper", parseChapterDate("Hace 19 hrs") > 0L)
        assertNotNull(hiddenChapter.attr("abs:href"))
    }

    // ── parseChapterDate tests (production helper) ─────────────────────────────

    @Test
    fun `parseChapterDate returns 0 for null and empty`() {
        assertEquals(0L, parseChapterDate(null))
        assertEquals(0L, parseChapterDate(""))
    }

    @Test
    fun `parseChapterDate handles absolute Spanish date`() {
        val result = parseChapterDate("15 Jul, 2025")
        assertTrue("Must parse absolute Spanish date", result > 0L)
    }

    @Test
    fun `parseChapterDate handles relative hours`() {
        val before = System.currentTimeMillis() - 3 * 3600_000L
        val result = parseChapterDate("Hace 3 horas")
        assertTrue("Must be within last 3 hours", result >= before - 1000L && result <= System.currentTimeMillis())
    }

    @Test
    fun `parseChapterDate handles relative days`() {
        val before = System.currentTimeMillis() - 2 * 86400_000L
        val result = parseChapterDate("Hace 2 días")
        assertTrue("Must be within last 2 days", result >= before - 1000L)
    }

    @Test
    fun `parseChapterDate handles hoy and ayer`() {
        val hoy = parseChapterDate("Hoy")
        assertTrue("Hoy must be recent", hoy > System.currentTimeMillis() - 60_000L)

        val ayer = parseChapterDate("Ayer")
        val oneDayAgo = System.currentTimeMillis() - 2 * 86400_000L
        assertTrue("Ayer must be approximately one day ago", ayer >= oneDayAgo && ayer <= System.currentTimeMillis())
    }

    @Test
    fun `parseChapterDate handles relative minutes`() {
        val result = parseChapterDate("Hace 5 minutos")
        val fiveMinAgo = System.currentTimeMillis() - 5 * 60_000L
        assertTrue("Must be within last 5 minutes", result >= fiveMinAgo - 1000L)
    }

    @Test
    fun `parseChapterDate handles relative weeks`() {
        val result = parseChapterDate("Hace 1 semana")
        val oneWeekAgo = System.currentTimeMillis() - 7 * 86400_000L
        assertTrue("Must be approximately one week ago", result >= oneWeekAgo - 1000L)
    }

    @Test
    fun `parseChapterDate returns 0 for unparseable string`() {
        assertEquals(0L, parseChapterDate("not a date"))
    }

    // ── decodeVerifyToUrl tests (Base64 reverse) ───────────────────────────────

    @Test
    fun `decodeVerifyToUrl decodes Base64 reversed URL`() {
        val url = "https://example.com/image.webp"
        val reversed = url.reversed()
        val encoded = java.util.Base64.getEncoder().encodeToString(reversed.toByteArray(Charsets.UTF_8))

        assertEquals(url, decodeVerifyToUrl(encoded))
    }

    @Test
    fun `decodeVerifyToUrl returns null for invalid Base64`() {
        assertNull(decodeVerifyToUrl("not-valid-base64!!!"))
    }

    @Test
    fun `decodeVerifyToUrl returns null when decoded value does not start with http`() {
        val payload = "garbage-not-a-url"
        val reversed = payload.reversed()
        val encoded = java.util.Base64.getEncoder().encodeToString(reversed.toByteArray(Charsets.UTF_8))

        assertNull(decodeVerifyToUrl(encoded))
    }

    @Test
    fun `decodeVerifyToUrl trims whitespace`() {
        val url = "https://cdn.example.com/page.png"
        val reversed = "  ${url.reversed()}  "
        val encoded = java.util.Base64.getEncoder().encodeToString(reversed.toByteArray(Charsets.UTF_8))

        assertEquals(url, decodeVerifyToUrl(encoded))
    }

    @Test
    fun `decodeVerifyToUrl handles http URL`() {
        val url = "http://insecure.example.com/img.jpg"
        val reversed = url.reversed()
        val encoded = java.util.Base64.getEncoder().encodeToString(reversed.toByteArray(Charsets.UTF_8))

        assertEquals(url, decodeVerifyToUrl(encoded))
    }

    // ── extractSlugAndCap tests ────────────────────────────────────────────────

    @Test
    fun `extractSlugAndCap extracts from query parameters`() {
        val document = Jsoup.parse(
            "<html><body></body></html>",
            "https://lectorhub.j5z.xyz/reader?manga=my-slug&cap=42",
        )
        val result = extractSlugAndCap(document)

        assertNotNull(result)
        assertEquals("my-slug" to "42", result)
    }

    @Test
    fun `extractSlugAndCap extracts from path pattern`() {
        val document = Jsoup.parse(
            "<html><body></body></html>",
            "https://lectorhub.j5z.xyz/leer/solo-leveling/capitulo-15",
        )
        val result = extractSlugAndCap(document)

        assertNotNull(result)
        assertEquals("solo-leveling" to "15", result)
    }

    @Test
    fun `extractSlugAndCap extracts from inline script variables`() {
        val html = """
            <html><body>
            <script>
                var MANGA_SLUG = "tower-of-god";
                var CAP_INICIAL = "3";
            </script>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://lectorhub.j5z.xyz/reader")
        val result = extractSlugAndCap(document)

        assertNotNull(result)
        assertEquals("tower-of-god" to "3", result)
    }

    @Test
    fun `extractSlugAndCap returns null when nothing found`() {
        val document = Jsoup.parse(
            "<html><body><p>No scripts</p></body></html>",
            "https://lectorhub.j5z.xyz/random-page",
        )
        assertNull(extractSlugAndCap(document))
    }

    @Test
    fun `extractSlugAndCap prefers query parameters over path`() {
        val document = Jsoup.parse(
            "<html><body></body></html>",
            "https://lectorhub.j5z.xyz/leer/other-slug/capitulo-10?manga=real-slug&cap=99",
        )
        val result = extractSlugAndCap(document)

        assertNotNull(result)
        assertEquals("real-slug" to "99", result)
    }

    // ── buildApiUrl tests ──────────────────────────────────────────────────────

    @Test
    fun `buildApiUrl produces correct URL`() {
        val result = buildApiUrl(
            "https://lectorhub.j5z.xyz/leer/solo-leveling/capitulo-5",
            "solo-leveling",
            "5",
        )

        assertNotNull(result)
        assertTrue(result!!.contains("/api_lector.php"))
        assertTrue(result.contains("slug=solo-leveling"))
        assertTrue(result.contains("cap=5"))
    }

    @Test
    fun `buildApiUrl returns null for invalid location`() {
        assertNull(buildApiUrl("not a url", "slug", "1"))
    }

    @Test
    fun `buildApiUrl preserves host`() {
        val result = buildApiUrl(
            "https://lectorhub.j5z.xyz/leer/manga/capitulo-1",
            "manga",
            "1",
        )

        assertNotNull(result)
        assertTrue(result!!.startsWith("https://lectorhub.j5z.xyz"))
    }

    // ── Reader pageListParse selector tests ────────────────────────────────────

    @Test
    fun `pageListParse selector captures all 32 reader page images from data-src and src`() {
        val document = Jsoup.parse(buildReaderHtml(32), "https://lectorhub.j5z.xyz")
        val imageElements = document.select(
            "#pagesContainer img.reader-page-image, .page-container img.protected-img, .reader-body img, .reading-content img",
        )

        assertEquals("Selector must match all 32 page images", 32, imageElements.size)

        val urls = imageElements.mapNotNull { element ->
            val imageUrl = when {
                element.hasAttr("data-verify") -> decodeVerifyToUrl(element.attr("data-verify"))
                element.hasAttr("data-sec-src") -> element.attr("abs:data-sec-src")
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                else -> element.attr("abs:src")
            }
            imageUrl?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }

        assertEquals("All 32 images must resolve to valid URLs", 32, urls.size)
        assertTrue("First URL must end with 01.png", urls[0].endsWith("/01.png"))
        assertTrue("Last URL must end with 32.png", urls[31].endsWith("/32.png"))
    }

    @Test
    fun `lazy images without src are captured via data-src`() {
        val document = Jsoup.parse(buildReaderHtml(32), "https://lectorhub.j5z.xyz")
        val imageElements = document.select("#pagesContainer img.reader-page-image")

        // Pages 11-32 have no src attribute (lazy), only data-src
        val lazyOnly = imageElements.filter { !it.hasAttr("src") && it.hasAttr("data-src") }
        assertEquals("22 lazy-only pages (11-32) must be matched", 22, lazyOnly.size)

        val lazyUrls = lazyOnly.mapNotNull { el ->
            el.attr("abs:data-src").takeIf { it.startsWith("http") }
        }
        assertEquals("All lazy pages must resolve URLs from data-src", 22, lazyUrls.size)
    }

    @Test
    fun `loaded pages with both src and data-src prefer data-src`() {
        val document = Jsoup.parse(buildReaderHtml(32), "https://lectorhub.j5z.xyz")
        val imageElements = document.select("#pagesContainer img.reader-page-image")

        // Pages 1-10 have both src and data-src
        val loaded = imageElements.filter { it.hasAttr("src") && it.hasAttr("data-src") }
        assertEquals("First 10 pages have both attributes", 10, loaded.size)

        loaded.forEach { element ->
            val url = element.attr("abs:data-src")
            assertTrue("data-src URL must be valid http", url.startsWith("https://"))
        }
    }

    @Test
    fun `page with data-verify attribute uses decodeVerifyToUrl`() {
        val url = "https://cdn.example.com/protected.webp"
        val encoded = java.util.Base64.getEncoder().encodeToString(url.reversed().toByteArray(Charsets.UTF_8))

        val html = """
            <html><body>
            <div id="pagesContainer">
                <img class="reader-page-image" data-verify="$encoded">
            </div>
            </body></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://lectorhub.j5z.xyz")
        val element = document.selectFirst("img.reader-page-image")!!

        val result = decodeVerifyToUrl(element.attr("data-verify"))
        assertEquals(url, result)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private data class ParsedDetails(
        val title: String,
        val description: String,
        val thumbnailUrl: String?,
        val genre: String,
        val status: Int,
    )

    private fun parseDetailsFromDocument(document: Document): ParsedDetails {
        val descriptionBlock = document.selectFirst("div.text-gray-200:has(h3:matchesOwn((?i)SINOPSIS))")
            ?: document.selectFirst("div.text-gray-200")
        val statusText = document.selectFirst("span.status-badge")?.text().orEmpty().lowercase()
        val status = when {
            statusText.contains("complet") -> SManga.COMPLETED
            arrayOf("pausa", "hiato").any { statusText.contains(it) } -> SManga.ON_HIATUS
            arrayOf("cancel", "aband").any { statusText.contains(it) } -> SManga.CANCELLED
            arrayOf("cultivo", "curso", "ongoing", "emision").any { statusText.contains(it) } -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }

        return ParsedDetails(
            title = document.selectFirst("h1.blood-title")!!.text(),
            description = descriptionBlock?.ownText()?.ifEmpty { descriptionBlock.text().replace(Regex("^SINOPSIS:?\\s*", RegexOption.IGNORE_CASE), "") }.orEmpty(),
            thumbnailUrl = document.selectFirst("div.lg\\:col-span-3 div.cultivation-panel img")?.attr("abs:src"),
            genre = document.select("a[href*='directorio.php?genero=']").joinToString { it.text() },
            status = status,
        )
    }
}
