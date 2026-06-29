package eu.kanade.tachiyomi.extension.es.leermangaesp

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class LeerMangaEsp : HttpSource() {

    override val name = "LeerMangaEsp"

    override val baseUrl = "https://$DOMAIN"

    override val lang = "es"

    override val supportsLatest = true

    private val chapterDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val allMangas = response.parseAs<List<HomeGridMangaDto>> { body: String ->
            val document = Jsoup.parse(body)
            val popularScript = document.selectFirst("script#populares-ssr")
            popularScript?.data().orEmpty()
        }

        return MangasPage(
            mangas = allMangas.mapNotNull { it.toSManga() },
            hasNextPage = false,
        )
    }

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("latest_chapters_with_dates")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val allMangas = response.parseAs<List<HomeGridMangaDto>>()
        val sortedMangas = allMangas.sortedByDescending { it.fecha_publicacion.orEmpty() }

        return MangasPage(
            mangas = sortedMangas.mapNotNull { it.toSManga() },
            hasNextPage = false,
        )
    }

    // ========================= Search =========================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val trimmed = query.trim()
        val mangaSlug = (trimmed.toHttpUrlOrNull() ?: "https://$trimmed".toHttpUrlOrNull())
            ?.takeIf { isSupportedDeeplink(it) }
            ?.pathSegments
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }

        if (mangaSlug != null) {
            return GET(mangaUrlFromSlug(mangaSlug), headers)
        }

        val selectedGenres = filters.filterIsInstance<GenreFilter>()
            .firstOrNull()
            ?.state
            ?.filter { it.state }
            ?.map { it.value }
            .orEmpty()

        val selectedType = filters.filterIsInstance<TypeFilter>()
            .firstOrNull()
            ?.toUriPart()
            ?.takeIf(String::isNotBlank)

        val url = searchApiUrl(
            page = page,
            query = query.trim().takeIf(String::isNotEmpty),
            type = selectedType,
            genres = selectedGenres,
        )

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestPath = response.request.url.encodedPath
        return if (requestPath.startsWith(MANGA_PATH_PREFIX)) {
            parseMangaDetails(response.asJsoup())
                .let { MangasPage(listOf(it), false) }
        } else {
            parseSearchMangaPage(response)
        }
    }

    // ========================= Filters =========================
    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
    )

    // ========================= Details =========================
    override fun getMangaUrl(manga: SManga): String = mangaUrlFromSlug(manga.url).toString()

    override fun mangaDetailsRequest(manga: SManga): Request = GET(mangaUrlFromSlug(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga = parseMangaDetails(response.asJsoup())

    // ========================= Chapters =========================
    override fun chapterListRequest(manga: SManga): Request = GET(mangaUrlFromSlug(manga.url), headers)

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterPath = chapter.url.toHttpUrlOrNull()?.encodedPath
            ?: chapter.url
                .trim()
                .takeIf(String::isNotEmpty)
                ?.let { if (it.startsWith('/')) it else "/$it" }
            ?: return baseUrl

        return baseUrl.toHttpUrl().newBuilder()
            .encodedPath(chapterPath)
            .build()
            .toString()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val seen = linkedSetOf<String>()
        val chapters = mutableListOf<SChapter>()

        var currentUrl = response.request.url
        var currentDocument = response.asJsoup()

        while (true) {
            currentDocument.parseChapterPage().forEach { chapter ->
                if (seen.add(chapter.url)) {
                    chapters += chapter
                }
            }

            val nextUrl = currentDocument.selectFirst("#more-link")
                ?.attr("href")
                ?.takeIf(String::isNotBlank)
                ?.let { currentUrl.resolve(it) }
                ?: break

            client.newCall(GET(nextUrl, headers)).execute().use { nextResponse ->
                currentUrl = nextResponse.request.url
                currentDocument = nextResponse.asJsoup()
            }
        }

        return chapters
    }

    // ========================= Pages =========================
    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#cascade-view img.manga-image").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun searchApiUrl(page: Int, query: String?, type: String?, genres: List<String>): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("api")
        .addPathSegment("buscar_mangas")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("page_size", PAGE_SIZE.toString())
        .apply {
            if (!query.isNullOrBlank()) {
                addQueryParameter("query", query)
            }
            if (!type.isNullOrBlank()) {
                addQueryParameter("tipo", type)
            }
            if (genres.isNotEmpty()) {
                addQueryParameter("generos", genres.joinToString(","))
            }
        }
        .build()

    private fun mangaUrlFromSlug(slug: String): HttpUrl {
        val normalizedSlug = slug.trim().removePrefix("/manga/").trim('/').substringBefore('/')

        return baseUrl.toHttpUrl().newBuilder()
            .encodedPath("$MANGA_PATH_PREFIX$normalizedSlug/")
            .build()
    }

    private fun isSupportedDeeplink(url: HttpUrl): Boolean {
        if (!url.host.contains("leermangaesp")) return false

        val pathSegments = url.pathSegments
        return when (pathSegments.getOrNull(0)?.lowercase(Locale.ROOT)) {
            "manga", "leer-m" -> !pathSegments.getOrNull(1).isNullOrBlank()
            else -> false
        }
    }

    private fun parseStatus(statusText: String): Int {
        val normalized = statusText.lowercase(Locale.ROOT)
        return when {
            "en curso" in normalized -> SManga.ONGOING
            "finalizado" in normalized || "completo" in normalized -> SManga.COMPLETED // NOTE: All entries are currently marked as 'en curso' by the source
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterDate(dateText: String?): Long = chapterDateFormat.tryParse(dateText?.trim())

    private fun parseSearchMangaPage(response: Response): MangasPage {
        val dto = response.parseAs<MangaListDto>()

        val mangas = dto.resultados
            .mapNotNull { it.toSManga() }

        return MangasPage(
            mangas = mangas,
            hasNextPage = dto.page < dto.total_pages,
        )
    }

    private fun Document.parseChapterPage(): List<SChapter> {
        return select("#chapter-list a.chapter-link").mapNotNull { element ->
            val href = element.attr("href")
            val chapterNumber = element.attr("data-chapter").trim()

            if (element.id() == "continue-link" || chapterNumber.isBlank()) {
                return@mapNotNull null
            }

            val chapterPath = baseUrl.toHttpUrl().resolve(href)?.encodedPath
                ?: return@mapNotNull null

            val chapterName = element.selectFirst(".chapter-title")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { element.text().trim() }

            if (chapterName.isBlank()) return@mapNotNull null

            SChapter.create().apply {
                url = chapterPath
                name = chapterName
                date_upload = parseChapterDate(element.selectFirst(".chapter-date")?.text())
            }
        }
    }

    private fun parseMangaDetails(document: Document): SManga {
        val slug = document.location().toHttpUrlOrNull()?.pathSegments?.getOrNull(1).orEmpty()
        val titleText = document.selectFirst(".manga-title, h1")?.text()?.trim().orEmpty()

        if (titleText.isBlank()) {
            throw Exception("Unable to parse manga details title")
        }

        return SManga.create().apply {
            url = slug
            title = titleText
            thumbnail_url = document.selectFirst("img.manga-cover")?.attr("abs:src")
            description = document.selectFirst("#synopsis-text")?.text()?.trim()
            genre = document.parseGenres()
            status = parseStatus(document.selectFirst("#info-block .info-value")?.text().orEmpty())
        }
    }

    private fun Document.parseGenres(): String? = select(".info-generos .genero-item")
        .map { it.text().trim() }
        .filter { it.isNotBlank() }
        .joinToString(", ")
        .takeIf { it.isNotBlank() }

    companion object {
        const val DOMAIN = "leermangaesp.net"
        const val PAGE_SIZE = 20
        const val MANGA_PATH_PREFIX = "/manga/"
        val IMAGE_BASE_URL = "https://images.$DOMAIN/file/leermangaesp".toHttpUrl()
    }
}
