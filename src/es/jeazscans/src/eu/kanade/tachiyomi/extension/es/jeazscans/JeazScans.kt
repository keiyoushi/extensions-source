package eu.kanade.tachiyomi.extension.es.jeazscans

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class JeazScans : HttpSource() {

    override val name = "Jeaz Scans"

    override val baseUrl = "https://lectorhub.j5z.xyz"

    override val lang = "es"

    override val supportsLatest = true

    override val id = 5292079548510508306

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val dateFormat by lazy {
        SimpleDateFormat("MMM dd, yyyy", Locale("es"))
    }

    // The site migrated to custom home sections and PHP routes for search.
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section:has(h3:matchesOwn((?i)Top Rankings)) a[href*='manga.php?id=']").map { element ->
            val manga = SManga.create()
            val link = element.attr("abs:href")
            val title = element.selectFirst("h4, h5")?.text().orEmpty()
            val thumb = element.selectFirst("img")?.attr("abs:src").orEmpty()

            manga.setUrlWithoutDomain(link)
            manga.title = title
            if (thumb.isNotBlank()) manga.thumbnail_url = thumb
            manga
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section:has(h3:contains(Lanzamientos)) .manga-card")
            .map { element ->
                val manga = SManga.create()
                val linkElement = element.selectFirst("a[href*='manga.php?id=']")
                val title = element.selectFirst("figcaption")?.text().orEmpty()
                val thumb = element.selectFirst("img")?.attr("abs:src").orEmpty()

                manga.setUrlWithoutDomain(linkElement?.attr("abs:href").orEmpty())
                manga.title = title
                if (thumb.isNotBlank()) manga.thumbnail_url = thumb
                manga
            }
            .filter { it.url.isNotEmpty() && it.title.isNotEmpty() }
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val titleText = document.selectFirst("h1.blood-title")?.text().orEmpty()
            if (titleText.isNotEmpty()) title = titleText

            val descriptionBlock = document.selectFirst("div.text-gray-200:has(h3:matchesOwn((?i)SINOPSIS))")
                ?: document.selectFirst("div.text-gray-200")
            descriptionBlock?.let {
                val synopsis = it.ownText().ifEmpty { it.text().replace(SINOPSIS_REGEX, "") }
                if (synopsis.isNotEmpty()) description = synopsis
            }

            val thumbnail = document.selectFirst("div.lg\\:col-span-3 div.cultivation-panel img")
                ?.attr("abs:src")
                .orEmpty()
            if (thumbnail.isNotEmpty()) thumbnail_url = thumbnail

            val genres = document.select("a[href*='directorio.php?genero=']")
                .map { it.text() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (genres.isNotEmpty()) genre = genres.joinToString()

            val statusText = document.selectFirst("span.status-badge")?.text().orEmpty()
            if (statusText.isNotEmpty()) {
                status = when {
                    statusText.contains("complet", ignoreCase = true) -> SManga.COMPLETED
                    statusText.contains("pausa", ignoreCase = true) || statusText.contains("hiato", ignoreCase = true) -> SManga.ON_HIATUS
                    statusText.contains("cancel", ignoreCase = true) || statusText.contains("aband", ignoreCase = true) -> SManga.CANCELLED
                    statusText.contains("cultivo", ignoreCase = true) || statusText.contains("curso", ignoreCase = true) ||
                        statusText.contains("ongoing", ignoreCase = true) || statusText.contains("emision", ignoreCase = true) -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chaptersContainer a.chapter-item").map { element ->
            val chapter = SChapter.create()

            val chapterUrl = element.attr("abs:href")
            chapter.setUrlWithoutDomain(chapterUrl)

            val chapterNumber = element.attr("data-chapter-number")
                .toFloatOrNull()
                ?: CHAPTER_NUMBER_REGEX
                    .find(chapterUrl)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toFloatOrNull()
                ?: -1f
            chapter.chapter_number = chapterNumber

            val chapterTitle = element.selectFirst(".chapter-title")?.text().orEmpty()
            chapter.name = if (chapterTitle.isNotEmpty()) {
                chapterTitle
            } else {
                "Chapter ${chapterNumber.toString().removeSuffix(".0")}"
            }

            val dateText = element.selectFirst("span:has(i.ph-clock)")?.text()
            chapter.date_upload = parseChapterDate(dateText)

            chapter
        }
    }

    private fun parseChapterDate(date: String?): Long {
        if (date.isNullOrEmpty()) return 0L
        val lowercaseDate = date.lowercase()
        return when {
            lowercaseDate.contains("hace") -> {
                val number = NUMBER_REGEX.find(lowercaseDate)?.value?.toIntOrNull() ?: return 0L
                val cal = Calendar.getInstance()
                when {
                    lowercaseDate.contains("segundo") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
                    lowercaseDate.contains("minuto") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
                    lowercaseDate.contains("hora") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
                    lowercaseDate.contains("día") || lowercaseDate.contains("dia") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
                    lowercaseDate.contains("semana") -> cal.apply { add(Calendar.WEEK_OF_YEAR, -number) }.timeInMillis
                    lowercaseDate.contains("mes") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
                    lowercaseDate.contains("año") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
                    else -> 0L
                }
            }
            lowercaseDate.contains("ayer") -> {
                Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
            }
            lowercaseDate.contains("hoy") -> {
                Calendar.getInstance().timeInMillis
            }
            else -> dateFormat.tryParse(date)
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val imageElements = document.select(
            "img.protected-img[src], .page-container img.protected-img[src], " +
                "img.mv-secure-img[data-sec-src], .reader-body img[data-sec-src], " +
                ".reader-body img[data-src], .reader-body img[src], " +
                ".reading-content img[data-src], .reading-content img[src]",
        )

        val htmlPages = imageElements.mapNotNull { element ->
            val imageUrl = when {
                element.hasAttr("data-sec-src") -> element.attr("abs:data-sec-src")
                element.hasAttr("data-src") -> element.attr("abs:data-src")
                element.hasAttr("src") -> element.attr("abs:src")
                else -> ""
            }

            if (imageUrl.isEmpty()) return@mapNotNull null

            imageUrl
        }.distinct().mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }

        if (htmlPages.isNotEmpty()) return htmlPages

        return fetchPagesFromApi(document)
    }

    private fun fetchPagesFromApi(document: Document): List<Page> {
        val (slug, cap) = extractSlugAndCap(document) ?: throw Exception("Could not extract slug/cap for API")
        val apiUrl = buildApiUrl(document.location(), slug, cap) ?: throw Exception("Could not build API URL")

        val requestHeaders = headers.newBuilder()
            .set("Referer", document.location())
            .build()

        val payload = client.newCall(GET(apiUrl, requestHeaders)).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP error ${response.code}")
            }

            val apiResponse = response.parseAs<ApiLectorResponse>()
            if (!apiResponse.success) throw Exception("API returned error")

            apiResponse
        }

        val pages = payload.paginas

        return pages.filter { it.dataVerify.isNotBlank() }
            .sortedBy { it.orden }
            .mapNotNull { decodeVerifyToUrl(it.dataVerify) }
            .distinct()
            .mapIndexed { idx, imageUrl -> Page(idx, imageUrl = imageUrl) }
    }

    private fun decodeVerifyToUrl(dataVerify: String): String? {
        val decoded = runCatching {
            String(Base64.decode(dataVerify, Base64.DEFAULT))
        }.getOrNull() ?: return null

        val url = decoded.reversed().trim()
        if (!url.startsWith("http")) return null
        return url
    }

    private fun extractSlugAndCap(document: Document): Pair<String, String>? {
        val locationUrl = document.location().toHttpUrlOrNull()
        val slugFromQuery = locationUrl?.queryParameter("manga")?.trim().orEmpty()
        val capFromQuery = locationUrl?.queryParameter("cap")?.trim().orEmpty()
        if (slugFromQuery.isNotBlank() && capFromQuery.isNotBlank()) {
            return slugFromQuery to capFromQuery
        }

        val fromPath = PATH_SLUG_CAP_REGEX
            .find(document.location())
            ?.groupValues
        if (fromPath != null && fromPath.size >= 3) {
            return fromPath[1] to fromPath[2]
        }

        val scriptContent = document.select("script").joinToString("\n") { it.data() + "\n" + it.html() }
        val slugFromScript = MANGA_SLUG_REGEX
            .find(scriptContent)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
        val capFromScript = CAP_INICIAL_REGEX
            .find(scriptContent)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()

        if (slugFromScript.isNotEmpty() && capFromScript.isNotEmpty()) {
            return slugFromScript to capFromScript
        }

        return null
    }

    private fun buildApiUrl(location: String, slug: String, cap: String): String? {
        val current = location.toHttpUrlOrNull() ?: return null

        return runCatching {
            current.newBuilder()
                .encodedPath("/api_lector.php")
                .setQueryParameter("slug", slug)
                .setQueryParameter("cap", cap)
                .build()
                .toString()
        }.getOrNull()
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isBlank()) {
        latestUpdatesRequest(page)
    } else {
        val url = "$baseUrl/ajax_search.php".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()
        GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.encodedPath.endsWith("/ajax_search.php")) {
            return latestUpdatesParse(response)
        }

        val items = response.parseAs<List<SearchResponseItem>>()
        val mangas = items.mapNotNull { it.toSManga(baseUrl) }

        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val SINOPSIS_REGEX = Regex("^SINOPSIS:?\\s*", RegexOption.IGNORE_CASE)
        private val CHAPTER_NUMBER_REGEX = Regex("capitulo-([0-9.]+)", RegexOption.IGNORE_CASE)
        private val NUMBER_REGEX = Regex("""\d+""")
        private val PATH_SLUG_CAP_REGEX = Regex("/leer/([^/]+)/capitulo-([0-9.]+)", RegexOption.IGNORE_CASE)
        private val MANGA_SLUG_REGEX = Regex("""MANGA_SLUG\s*=\s*["']([^"']+)["']""")
        private val CAP_INICIAL_REGEX = Regex("""CAP_INICIAL\s*=\s*["']([^"']+)["']""")
    }
}
