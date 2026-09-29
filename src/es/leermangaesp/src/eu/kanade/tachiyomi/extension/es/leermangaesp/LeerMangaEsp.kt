package eu.kanade.tachiyomi.extension.es.leermangaesp

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LeerMangaEsp : KeiSource() {

    private val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)

    private val imageBaseUrl: HttpUrl
        get() = (baseUrl.replace("https://", "https://images.") + "/file/leermangaesp").toHttpUrl()

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val allMangas = client.get(baseUrl).asJsoup()
            .selectFirst("script#ssr-trends-data")
            ?.data()
            .orEmpty()
            .parseAs<List<HomeGridMangaDto>>()

        return MangasPage(
            mangas = allMangas.mapNotNull { it.toSManga(imageBaseUrl) },
            hasNextPage = false,
        )
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("latest_chapters_with_dates")
            .build()

        val allMangas = client.get(url).parseAs<List<HomeGridMangaDto>>()
        val sortedMangas = allMangas.sortedByDescending { it.fechaPublicacion.orEmpty() }

        return MangasPage(
            mangas = sortedMangas.mapNotNull { it.toSManga(imageBaseUrl) },
            hasNextPage = false,
        )
    }

    // ========================= Search =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val mangaSlug = url.takeIf { isSupportedDeeplink(it) }
            ?.pathSegments
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return parseMangaDetails(client.get(mangaUrlFromSlug(mangaSlug)).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.map { it.value }
            .orEmpty()

        val selectedType = filters.firstInstanceOrNull<TypeFilter>()
            ?.toUriPart()
            ?.takeIf(String::isNotBlank)

        val url = searchApiUrl(
            page = page,
            query = query.trim().takeIf(String::isNotEmpty),
            type = selectedType,
            genres = selectedGenres,
        )

        val dto = client.get(url).parseAs<MangaListDto>()

        return MangasPage(
            mangas = dto.resultados.mapNotNull { it.toSManga(imageBaseUrl) },
            hasNextPage = dto.page < dto.totalPages,
        )
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
        GenreFilter(),
    )

    // ========================= Details =========================
    override fun getMangaUrl(manga: SManga): String = mangaUrlFromSlug(manga.url).toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var document = client.get(mangaUrlFromSlug(manga.url)).asJsoup()
        val details = parseMangaDetails(document)

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val seen = linkedSetOf<String>()
        val chapterList = mutableListOf<SChapter>()

        while (true) {
            document.parseChapterPage().forEach { chapter ->
                if (seen.add(chapter.url)) {
                    chapterList += chapter
                }
            }

            val nextUrl = document.selectFirst("#more-link")
                ?.attr("href")
                ?.takeIf(String::isNotBlank)
                ?.let { document.location().toHttpUrl().resolve(it) }
                ?: break

            document = client.get(nextUrl).asJsoup()
        }

        return SMangaUpdate(details, chapterList)
    }

    // ========================= Chapters =========================
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

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#cascade-view img.manga-image").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:src"))
        }
    }

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
        val normalizedSlug = slug.trim().removePrefix("/info/").trim('/').substringBefore('/')

        return baseUrl.toHttpUrl().newBuilder()
            .encodedPath("$MANGA_PATH_PREFIX$normalizedSlug/")
            .build()
    }

    private fun isSupportedDeeplink(url: HttpUrl): Boolean {
        if (!url.host.contains("mangalect")) return false

        val pathSegments = url.pathSegments
        return when (pathSegments.getOrNull(0)?.lowercase(Locale.ROOT)) {
            "info", "manga", "leer-m" -> !pathSegments.getOrNull(1).isNullOrBlank()
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
                date_upload = chapterDateFormat.tryParseDate(element.selectFirst(".chapter-date")?.text()?.trim())
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
        const val PAGE_SIZE = 20
        const val MANGA_PATH_PREFIX = "/info/"
    }
}
