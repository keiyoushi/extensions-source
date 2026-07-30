package eu.kanade.tachiyomi.extension.es.mantrazscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferences
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.Jsoup

@Source
abstract class ManhwaScan :
    HttpSource(),
    ConfigurableSource {

    override val baseUrl: String
        get() = preferences.getString(BASE_URL_PREF, defaultBaseUrl)!!

    private val defaultBaseUrl = "https://mantrazscans.lat"

    private val preferences: SharedPreferences = getPreferences()

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val isPageImage = request.url.host == baseUrl.toHttpUrl().host &&
                request.url.encodedPath.startsWith(PAGE_IMAGE_PATH_PREFIX)

            if (!isPageImage) {
                return@addInterceptor chain.proceed(request)
            }

            val response = chain.proceed(
                request.newBuilder()
                    .removeHeader("Accept-Encoding")
                    .header("Accept-Encoding", "identity")
                    .build(),
            )
            val body = response.body
            val mediaType = body.contentType()
            val bytes = body.bytes()

            response.newBuilder()
                .removeHeader("Content-Encoding")
                .removeHeader("Content-Length")
                .body(bytes.toResponseBody(mediaType))
                .build()
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(if (page == 1) baseUrl else exploreUrl(page), headers)

    override fun popularMangaParse(response: Response): MangasPage = if (isHomePage(response)) {
        parseTrendingPage(response)
    } else {
        parseSeriesGrid(response)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(if (page == 1) baseUrl else exploreUrl(page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = if (isHomePage(response)) {
        parseSeriesGrid(response, forceHasNextPage = true)
    } else {
        parseSeriesGrid(response)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return GET(exploreUrl(page), headers)

        val basePath = if (page > 1) "$baseUrl/explorar/page/$page/" else "$baseUrl/explorar/"
        val url = basePath.toHttpUrl().newBuilder()
            .addQueryParameter("q", trimmedQuery)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSeriesGrid(response)

    private fun parseSeriesGrid(response: Response, forceHasNextPage: Boolean = false): MangasPage {
        val document = response.asJsoup()
        val mangas = document.selectFirst("main .series-grid")
            ?.select(".s-card")
            ?.mapNotNull { card ->
                val link = card.selectFirst("a.s-card-title[href]") ?: return@mapNotNull null

                SManga.create().apply {
                    title = link.text().trim()
                    url = link.attr("href")
                        .substringAfter(baseUrl)
                        .ensureStartsWithSlash()
                    thumbnail_url = card.selectFirst(".s-card-img img[src]")?.attr("abs:src")
                }
            }
            .orEmpty()

        val hasNextPage = forceHasNextPage || document.selectFirst(".pager-btn[href]:matchesOwn(Siguiente)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseTrendingPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".tsl-slide").mapNotNull { slide ->
            val title = slide.selectFirst(".tsl-title")?.text()?.trim().orEmpty()
            val link = slide.selectFirst("a.tsl-cover[href], a.tsl-cta[href]") ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title.ifEmpty { link.attr("title") }
                url = link.attr("href")
                    .substringAfter(baseUrl)
                    .ensureStartsWithSlash()
                thumbnail_url = slide.selectFirst(".tsl-cover img[src]")?.attr("abs:src")
            }
        }

        return MangasPage(mangas, true)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".series-title")?.text()?.trim().orEmpty()
            thumbnail_url = document.selectFirst(".series-cover img[src], .series-hero img[src]")?.attr("abs:src")
            description = document.selectFirst(".series-desc")?.text()?.trim()
            genre = document.select(".series-tags a, .series-tags span")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
                .ifBlank { null }
            status = parseStatus(document.selectFirst(".badge-pill, .badge-ongoing")?.text())
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body.string()
        val mangaPath = response.request.url.encodedPath.trimEnd('/')

        val chapters = parseEmbeddedChapterNumbers(body).map { chapterNumber ->
            SChapter.create().apply {
                name = "Capítulo $chapterNumber"
                url = chapterUrl(mangaPath, chapterNumber)
                chapter_number = chapterNumber.toFloatOrNull() ?: 0f
            }
        }.sortedByDescending { it.chapter_number }

        if (chapters.isNotEmpty()) return chapters

        val document = Jsoup.parse(body, baseUrl)
        return document.select(".chapters-grid .ch-row[href]").mapNotNull { link ->
            val chapterNumber = link.attr("href").trimEnd('/').substringAfterLast("capitulo-")
            if (chapterNumber.isBlank()) return@mapNotNull null

            SChapter.create().apply {
                name = link.text().trim().ifEmpty { "Capítulo $chapterNumber" }
                url = link.attr("href")
                    .substringAfter(baseUrl)
                    .ensureStartsWithSlash()
                chapter_number = chapterNumber.toFloatOrNull() ?: 0f
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = Jsoup.parse(response.body.string(), response.request.url.toString())
        val pages = document.select(
            "main img[src*='/WP-manga/data/'], " +
                "main img[alt*='Página'], " +
                "link[rel=preload][as=image][href*='/WP-manga/data/']",
        )
            .mapNotNull { element ->
                when (element.tagName()) {
                    "link" -> element.attr("href")
                    else -> element.attr("src")
                        .ifBlank { element.attr("data-src") }
                        .ifBlank { element.attr("data-lazy-src") }
                }.toAbsoluteImageUrl()
            }
            .distinct()

        return pages.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()

    private fun exploreUrl(page: Int): String = if (page > 1) {
        "$baseUrl/explorar/page/$page/"
    } else {
        "$baseUrl/explorar/"
    }

    private fun isHomePage(response: Response): Boolean = response.request.url.encodedPath == "/"

    private fun parseStatus(text: String?): Int = when {
        text.isNullOrBlank() -> SManga.UNKNOWN
        text.contains("emisión", true) || text.contains("en emisión", true) || text.contains("ongoing", true) -> SManga.ONGOING
        text.contains("finalizado", true) || text.contains("completo", true) -> SManga.COMPLETED
        text.contains("pausa", true) || text.contains("hiatus", true) -> SManga.ON_HIATUS
        text.contains("cancel", true) -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseEmbeddedChapterNumbers(body: String): List<String> {
        val raw = CHAPTERS_REGEX.find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?: return emptyList()

        val cleaned = raw.replace(RSC_SPLIT_NOISE, "")

        return CHAPTER_NUM_REGEX.findAll(cleaned)
            .map { it.value }
            .distinct()
            .toList()
    }

    private fun chapterUrl(mangaPath: String, chapterNumber: String): String {
        val num = chapterNumber.toFloatOrNull()
        return if (num != null && num == num.toLong().toFloat()) {
            "$mangaPath/capitulo-${num.toLong().toInt()}/"
        } else {
            "$mangaPath/capitulo-$chapterNumber/"
        }
    }

    private fun String.ensureStartsWithSlash(): String = if (startsWith('/')) this else "/$this"

    private fun String.toAbsoluteImageUrl(): String? {
        if (isBlank()) return null
        return baseUrl.toHttpUrl()
            .resolve(this)
            ?.toString()
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Editar URL de la fuente"
            summary = "Para uso temporal, si la extensión se actualiza se perderá el cambio."
            dialogTitle = "Editar URL de la fuente"
            dialogMessage = "URL por defecto:\n$defaultBaseUrl"
            setDefaultValue(defaultBaseUrl)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Reinicie la aplicación para aplicar los cambios", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val PAGE_IMAGE_PATH_PREFIX = "/img/WP-manga/data/"

        private val CHAPTERS_REGEX = Regex("\\\\\\\"chapters\\\\\\\":\\[(.*?)\\\\\\\"slug\\\\\"", setOf(RegexOption.DOT_MATCHES_ALL))
        private val CHAPTER_NUM_REGEX = Regex("[0-9]+(?:\\.[0-9]+)?")
        private val RSC_SPLIT_NOISE = "\"])</script><script>self.__next_f.push([1,\""
    }
}
