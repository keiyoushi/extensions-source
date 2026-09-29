package eu.kanade.tachiyomi.extension.pl.mangahona

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter

@Source
abstract class MangaHoNa : KeiSource() {

    override val supportsLatest = false

    private val apiBaseUrl = "https://api.mangahona.pl/v1"

    private val cdnBaseUrl = "https://cdn.mangahona.pl"

    // ========================= Popular =========================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangaList = client.get("$apiBaseUrl/manga").parseAs<List<MangaDto>>()
        return MangasPage(mangaList.map { it.toSManga() }, false)
    }

    // ========================= Latest =========================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangaList = client.get("$apiBaseUrl/manga").parseAs<List<MangaDto>>()
        val filtered = if (query.isNotBlank()) {
            mangaList.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            mangaList
        }
        return MangasPage(filtered.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val pathSegments = url.pathSegments
        val mangaId = when {
            pathSegments.size >= 2 && pathSegments[0] == "manga" -> pathSegments[1]
            pathSegments.size >= 2 && pathSegments[0] == "czytaj" -> pathSegments[1]
            else -> return null
        }
        return client.get("$apiBaseUrl/manga/$mangaId").parseAs<MangaDto>().toSManga()
    }

    private fun MangaDto.toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnailUrl(coverImage)
    }

    private fun thumbnailUrl(coverImage: String?) = coverImage?.let {
        "$cdnBaseUrl/images.php".toHttpUrl().newBuilder()
            .addQueryParameter("url", it)
            .addQueryParameter("w", "1900")
            .build()
            .toString()
    }

    // ========================= Details =========================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (manga.url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        return coroutineScope {
            val details = if (fetchDetails) async { fetchDetails(manga) } else null
            val chapterList = if (fetchChapters) async { fetchChapters(manga) } else null
            SMangaUpdate(details?.await() ?: manga, chapterList?.await() ?: chapters)
        }
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val dto = client.get("$apiBaseUrl/manga/${manga.url}").parseAs<MangaDto>()
        return SManga.create().apply {
            url = dto.id.toString()
            title = dto.name
            description = dto.description?.replace("\r\n", "\n")?.trim()
            author = dto.author?.trim()
            thumbnail_url = thumbnailUrl(dto.coverImage)
            genre = buildGenreString(dto.genere, dto.tag)
            status = when (dto.status) {
                "completed", "Completed" -> SManga.COMPLETED
                "ongoing", "Ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    private suspend fun buildGenreString(genereIds: String?, tagIds: String?): String? {
        val categories = fetchCategories() ?: return null
        val genres = genereIds?.split(";")?.mapNotNull { id ->
            categories.generes.find { it.id.toString() == id.trim() }?.name
        }.orEmpty()
        val tags = tagIds?.split(";")?.mapNotNull { id ->
            categories.tags.find { it.id.toString() == id.trim() }?.name
        }.orEmpty()
        val combined = genres + tags
        return combined.takeIf { it.isNotEmpty() }?.joinToString()
    }

    private var categoriesCache: CategoriesDto? = null

    private suspend fun fetchCategories(): CategoriesDto? {
        if (categoriesCache != null) return categoriesCache
        return try {
            client.get("$apiBaseUrl/categories")
                .parseAs<CategoriesDto>()
                .also { categoriesCache = it }
        } catch (_: Exception) {
            null
        }
    }

    // ========================= Chapters =========================

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        val chapters = client.get("$apiBaseUrl/chapters/${manga.url}").parseAs<List<ChapterDto>>()
        return chapters.map { dto ->
            SChapter.create().apply {
                url = "/czytaj/${manga.url}/${dto.chapterIndex.content}"
                name = dto.chapterName
                date_upload = dateFormat.tryParseDateTime(dto.date)
            }
        }.reversed()
    }

    // ========================= Pages =========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.startsWith("/manga/")) {
            throw Exception("Migrate from $name to $name (same extension)")
        }
        val pathParts = chapter.url.removePrefix("/czytaj/").split("/")
        val mangaId = pathParts[0]
        val chapterIndex = pathParts[1]
        val chapterData = client.get("$apiBaseUrl/chapterData/$mangaId/$chapterIndex").parseAs<ChapterDataDto>()
        val pages = chapterData.data.parseAs<Map<String, PageDto>>()
        return pages.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .mapIndexed { index, entry ->
                Page(index, imageUrl = entry.value.src)
            }
    }

    companion object {
        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss")
    }
}
