package eu.kanade.tachiyomi.extension.es.nexusscanlation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Nexusscanlation : ParsedHttpSource() {

    override val id = 3189427615021467091L
    override val name = "NexusScanlation"
    override val baseUrl = "https://nexusscanlation.com"
    override val lang = "es"
    override val supportsLatest = true

    private val apiBaseUrl = "https://api.nexusscanlation.com/api/v1"

    override fun headersBuilder() = super.headersBuilder()
        .add(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        )

    private fun apiHeaders() = headersBuilder()
        .add("Accept", "application/json")
        .add("Referer", baseUrl)
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$apiBaseUrl/catalog?page=$page", apiHeaders())

    override fun popularMangaParse(response: Response): MangasPage {
        val root = JSONObject(response.body?.string().orEmpty())
        val data = root.optJSONArray("data") ?: JSONArray()
        val mangas = parseCatalogArray(data)
        val hasNext = root.optJSONObject("meta")?.optBoolean("has_next", false) ?: false
        return MangasPage(mangas, hasNext)
    }

    override fun popularMangaSelector() = "_unused_"

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = "_unused_"

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBaseUrl/catalog?page=$page", apiHeaders())

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            return GET("$apiBaseUrl/catalog/search?q=$encoded&page=$page", apiHeaders())
        }

        return GET("$apiBaseUrl/catalog?page=$page", apiHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val slug = extractSeriesSlug(document)
        if (slug.isNotBlank()) {
            val apiResponse = client.newCall(GET("$apiBaseUrl/series/$slug", apiHeaders())).execute()
            apiResponse.use { res ->
                val root = JSONObject(res.body?.string().orEmpty())
                val serie = root.optJSONObject("serie")
                if (serie != null) {
                    return seriesToManga(serie)
                }
            }
        }

        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text().orEmpty()
        manga.thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        manga.description = document.selectFirst("meta[name=description]")?.attr("content")
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.trim('/').substringAfterLast('/')
        return GET("$apiBaseUrl/series/$slug", apiHeaders())
    }

    override fun chapterListSelector() = "_unused_"

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    override fun chapterListParse(response: Response): List<SChapter> {
        val root = JSONObject(response.body?.string().orEmpty())
        val serie = root.optJSONObject("serie") ?: JSONObject()
        val seriesSlug = serie.optString("slug")
        val chapters = root.optJSONArray("capitulos") ?: JSONArray()

        val parsed = mutableListOf<SChapter>()
        for (i in 0 until chapters.length()) {
            val item = chapters.optJSONObject(i) ?: continue
            val capSlug = item.optString("slug")
            if (capSlug.isBlank()) continue

            val number = item.optDouble("numero", Double.NaN)
            val numberLabel = when {
                number.isNaN() -> capSlug
                number % 1.0 == 0.0 -> number.toInt().toString()
                else -> number.toString()
            }

            val chapter = SChapter.create()
            chapter.setUrlWithoutDomain("/series/$seriesSlug/chapter/$capSlug")
            chapter.name = "Capítulo $numberLabel"
            chapter.chapter_number = if (number.isNaN()) -1f else number.toFloat()
            chapter.date_upload = parseIsoDate(item.optString("published_at"))
            parsed.add(chapter)
        }

        return parsed.sortedWith(compareByDescending<SChapter> { chapterSortKey(it) }.thenByDescending { it.name.orEmpty() })
    }

    private fun chapterSortKey(chapter: SChapter): Float {
        val number = chapter.chapter_number
        return if (number < 0f) Float.MIN_VALUE else number
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(document: Document): List<Page> {
        val chapterUrl = document.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
        val match = Regex("""/series/([^/]+)/chapter/([^/?#]+)""").find(chapterUrl)
        val seriesSlug = match?.groupValues?.getOrNull(1).orEmpty()
        val chapterSlug = match?.groupValues?.getOrNull(2).orEmpty()

        if (seriesSlug.isNotBlank() && chapterSlug.isNotBlank()) {
            val apiResponse = client.newCall(GET("$apiBaseUrl/series/$seriesSlug/capitulos/$chapterSlug", apiHeaders())).execute()
            apiResponse.use { res ->
                val root = JSONObject(res.body?.string().orEmpty())
                val data = root.optJSONObject("data") ?: JSONObject()
                val pages = data.optJSONArray("paginas") ?: JSONArray()

                val parsed = mutableListOf<Pair<Int, String>>()
                for (i in 0 until pages.length()) {
                    val page = pages.optJSONObject(i) ?: continue
                    val order = page.optInt("orden", i + 1)
                    val imageUrl = page.optString("url")
                    if (imageUrl.isNotBlank()) {
                        parsed.add(order to imageUrl)
                    }
                }

                if (parsed.isNotEmpty()) {
                    return parsed
                        .sortedBy { it.first }
                        .mapIndexed { index, pair -> Page(index, "", pair.second) }
                }
            }
        }

        return document.select("img[src]")
            .mapNotNull { img -> img.absUrl("src").takeIf { it.contains("cdn.nexusscanlation.com") } }
            .distinct()
            .mapIndexed { index, imageUrl -> Page(index, "", imageUrl) }
    }

    private fun parseCatalogArray(array: JSONArray): List<SManga> {
        val mangas = mutableListOf<SManga>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val slug = item.optString("slug")
            if (slug.isBlank()) continue

            val title = item.optString("titulo")
            if (title.isBlank()) continue

            val manga = SManga.create()
            manga.setUrlWithoutDomain("/series/$slug")
            manga.title = title
            manga.thumbnail_url = item.optString("portada_url").ifBlank { null }
            mangas.add(manga)
        }
        return mangas
    }

    private fun extractSeriesSlug(document: Document): String {
        val canonical = document.selectFirst("link[rel=canonical]")?.attr("href").orEmpty()
        Regex("""/series/([^/?#]+)""").find(canonical)?.let {
            return it.groupValues[1]
        }

        val chapterLink = document.selectFirst("a[href*='/series/'][href*='/chapter/']")?.attr("href").orEmpty()
        Regex("""/series/([^/]+)/chapter/""").find(chapterLink)?.let {
            return it.groupValues[1]
        }

        return ""
    }

    private fun seriesToManga(serie: JSONObject): SManga {
        val manga = SManga.create()
        manga.title = serie.optString("titulo")
        manga.thumbnail_url = serie.optString("portada_url").ifBlank { null }
        manga.description = serie.optString("descripcion").ifBlank { null }

        val genres = serie.optJSONArray("generos") ?: JSONArray()
        val genreNames = mutableListOf<String>()
        for (i in 0 until genres.length()) {
            val genre = genres.optJSONObject(i) ?: continue
            val name = genre.optString("nombre")
            if (name.isNotBlank()) genreNames.add(name)
        }
        manga.genre = genreNames.joinToString().ifBlank { null }

        val status = serie.optString("estado").lowercase(Locale.ROOT)
        manga.status = when (status) {
            "en_emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val authors = serie.optJSONArray("autores") ?: JSONArray()
        if (authors.length() > 0) {
            val names = mutableListOf<String>()
            for (i in 0 until authors.length()) {
                val authorObj = authors.optJSONObject(i)
                val authorName = authorObj?.optString("nombre") ?: authors.optString(i)
                if (!authorName.isNullOrBlank()) names.add(authorName)
            }
            if (names.isNotEmpty()) {
                manga.author = names.joinToString()
                manga.artist = names.joinToString()
            }
        }

        return manga
    }

    private fun parseIsoDate(value: String): Long {
        if (value.isBlank()) return 0L
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return try {
            format.parse(value)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun imageUrlParse(document: Document): String = ""
}
