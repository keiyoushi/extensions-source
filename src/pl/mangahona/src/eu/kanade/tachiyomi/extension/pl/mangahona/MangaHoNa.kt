package eu.kanade.tachiyomi.extension.pl.mangahona

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaHoNa : HttpSource() {

    override val supportsLatest = false

    private val apiBaseUrl = "https://api.mangahona.pl/v1"

    private val cdnBaseUrl = "https://cdn.mangahona.pl"

    override fun headersBuilder() = super.headersBuilder()

    // ========================= Popular =========================

    override fun popularMangaRequest(page: Int): Request = GET("$apiBaseUrl/manga", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangaList = response.parseAs<List<MangaDto>>()
        val mangas = mangaList.map { dto ->
            SManga.create().apply {
                url = dto.id
                title = dto.name
                thumbnail_url = dto.coverImage?.let {
                    "$cdnBaseUrl/images.php".toHttpUrl().newBuilder()
                        .addQueryParameter("url", it)
                        .addQueryParameter("w", "1900")
                        .build()
                        .toString()
                }
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = query.toHttpUrlOrNull()
        if (url != null && url.host == baseUrl.toHttpUrl().host) {
            val pathSegments = url.pathSegments
            val mangaId = when {
                pathSegments.size >= 2 && pathSegments[0] == "manga" -> pathSegments[1]
                pathSegments.size >= 2 && pathSegments[0] == "czytaj" -> pathSegments[1]
                else -> null
            }
            if (mangaId != null) {
                return GET("$apiBaseUrl/manga/$mangaId", headers)
            }
        }
        return GET("$apiBaseUrl/manga#$query", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url
        val isDeeplink = url.pathSegments.lastOrNull()?.let { it != "manga" } ?: false

        if (isDeeplink) {
            val dto = response.parseAs<MangaDto>()
            val manga = SManga.create().apply {
                this.url = dto.id
                title = dto.name
                thumbnail_url = dto.coverImage?.let {
                    "$cdnBaseUrl/images.php".toHttpUrl().newBuilder()
                        .addQueryParameter("url", it)
                        .addQueryParameter("w", "1900")
                        .build()
                        .toString()
                }
            }
            return MangasPage(listOf(manga), false)
        }

        val query = response.request.url.fragment.orEmpty()
        val mangaList = response.parseAs<List<MangaDto>>()
        val filtered = if (query.isNotBlank()) {
            mangaList.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            mangaList
        }
        val mangas = filtered.map { dto ->
            SManga.create().apply {
                this.url = dto.id
                title = dto.name
                thumbnail_url = dto.coverImage?.let {
                    "$cdnBaseUrl/images.php".toHttpUrl().newBuilder()
                        .addQueryParameter("url", it)
                        .addQueryParameter("w", "1900")
                        .build()
                        .toString()
                }
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return GET("$apiBaseUrl/manga/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<MangaDto>()
        return SManga.create().apply {
            url = dto.id
            title = dto.name
            description = dto.description?.replace("\r\n", "\n")?.trim()
            author = dto.author?.trim()
            thumbnail_url = dto.coverImage?.let {
                "$cdnBaseUrl/images.php".toHttpUrl().newBuilder()
                    .addQueryParameter("url", it)
                    .addQueryParameter("w", "1900")
                    .build()
                    .toString()
            }
            genre = buildGenreString(dto.genere, dto.tag)
            status = when (dto.status) {
                "completed", "Completed" -> SManga.COMPLETED
                "ongoing", "Ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    private fun buildGenreString(genereIds: String?, tagIds: String?): String? {
        val categories = fetchCategories() ?: return null
        val genres = genereIds?.split(";")?.mapNotNull { id ->
            categories.generes.find { it.id == id.trim() }?.name
        }.orEmpty()
        val tags = tagIds?.split(";")?.mapNotNull { id ->
            categories.tags.find { it.id == id.trim() }?.name
        }.orEmpty()
        val combined = genres + tags
        return combined.takeIf { it.isNotEmpty() }?.joinToString()
    }

    private var categoriesCache: CategoriesDto? = null

    private fun fetchCategories(): CategoriesDto? {
        if (categoriesCache != null) return categoriesCache
        return try {
            client.newCall(GET("$apiBaseUrl/categories", headers)).execute()
                .parseAs<CategoriesDto>()
                .also { categoriesCache = it }
        } catch (_: Exception) {
            null
        }
    }

    // ========================= Chapters =========================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return GET("$apiBaseUrl/chapters/${manga.url}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaId = response.request.url.pathSegments.last()
        val chapters = response.parseAs<List<ChapterDto>>()
        return chapters.map { dto ->
            SChapter.create().apply {
                url = "/czytaj/$mangaId/${dto.chapterIndex}"
                name = dto.chapterName
                date_upload = dto.date?.let { dateFormat.tryParse(it) } ?: 0L
            }
        }.reversed()
    }

    // ========================= Pages =========================

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        val pathParts = chapter.url.removePrefix("/czytaj/").split("/")
        val mangaId = pathParts[0]
        val chapterIndex = pathParts[1]
        return GET("$apiBaseUrl/chapterData/$mangaId/$chapterIndex", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterData = response.parseAs<ChapterDataDto>()
        val pages = chapterData.data.parseAs<Map<String, PageDto>>()
        return pages.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .mapIndexed { index, entry ->
                Page(index, imageUrl = entry.value.src)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        }
    }
}
