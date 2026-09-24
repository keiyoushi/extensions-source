package eu.kanade.tachiyomi.extension.en.toonz

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Toonz : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)
        .addCookie(
            listOf(
                "adult_ok" to "1",
                "reader_prefs" to "%7B%22rating%22%3A%22pornographic%22%7D",
            ),
        )

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("$baseUrl/comics?sort=popular&page=$page", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("$baseUrl/comics?sort=latest&page=$page", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            return getMangaList("$baseUrl/search?q=$encodedQuery&page=$page", page)
        }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.getValue() ?: "popular"

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.getValue().orEmpty()
        if (genre.isNotBlank()) {
            return getMangaList("$baseUrl/genre/$genre?sort=$sort&page=$page", page)
        }

        val catalog = filters.firstInstanceOrNull<CatalogFilter>()?.getValue() ?: "comics"
        return getMangaList("$baseUrl/$catalog?sort=$sort&page=$page", page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val type = url.pathSegments.firstOrNull() ?: return null
        if (type !in VALID_TYPES) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            setUrlWithoutDomain("/$type/$slug")
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicId = manga.memo.getStringOrNull("comicId")
        if (comicId != null) {
            val (updatedManga, updatedChapters) = coroutineScope {
                val mangaDeferred = async { if (fetchDetails) fetchMangaDetails(manga) else manga }
                val chaptersDeferred = async { if (fetchChapters) fetchChapters(manga, comicId) else chapters }
                mangaDeferred.await() to chaptersDeferred.await()
            }
            return SMangaUpdate(updatedManga, updatedChapters)
        }

        val updatedManga = fetchMangaDetails(manga)
        val resolvedComicId = updatedManga.memo.getString("comicId")
        val updatedChapters = if (fetchChapters) {
            fetchChapters(manga, resolvedComicId)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl).asJsoup()
        val comicId = manga.memo.getStringOrNull("comicId") ?: document.select("script").firstNotNullOfOrNull { script ->
            COMIC_ID_REGEX.find(script.data())?.groupValues?.get(1)
        } ?: throw Exception("Could not find comic ID for ${manga.title}")

        return manga.apply {
            title = document.selectFirst("h1")?.text() ?: title
            thumbnail_url = document.selectFirst("img[data-cover]")?.absUrl("src")
                ?: document.selectFirst("div.group img[src]")?.absUrl("src")
                ?: thumbnail_url
            description = document.selectFirst("div.prose")?.text()
                ?: document.selectFirst("meta[name=description]")?.attr("content")?.trim()
            author = document.select("a[href^=/author/]").joinToString { it.text() }.takeIf { it.isNotBlank() }
            artist = author
            genre = document.select("a[href^=/genre/]").joinToString { it.text() }.takeIf { it.isNotBlank() }
            status = parseStatus(document.selectFirst("dt:contains(Status) + dd")?.text())
            memo = buildJsonObject {
                put("comicId", comicId)
            }
        }
    }

    private suspend fun fetchChapters(manga: SManga, comicId: String): List<SChapter> {
        val chaptersResponse = client.get("$baseUrl/api/comics/$comicId/chapters")
        val chapterListDto = chaptersResponse.parseAs<ChapterListDto>()
        return chapterListDto.chapters.map { it.toSChapter(manga.url) }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.memo.getString("mangaUrl")}/chapter/${chapter.url}"

    private val rscHeaders: Headers
        get() = headersBuilder()
            .add("rsc", "1")
            .build()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val images = client.get(chapterUrl, rscHeaders).extractNextJs<ChapterImagesDto>()?.images
            ?: return emptyList()

        return images.mapIndexed { index, image ->
            val filename = image.filename.removePrefix("/")
            Page(index, imageUrl = "$baseUrl/uploads/$filename")
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/genres").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()
            ?.map { it.name to it.slug }
            .orEmpty()

        val genreOptions = listOf("None" to "") + genres

        return FilterList(
            CatalogFilter(),
            SortFilter(),
            Filter.Separator(),
            GenreFilter(genreOptions),
        )
    }

    private suspend fun getMangaList(url: String, page: Int): MangasPage {
        val document = client.get(url).asJsoup()
        val seen = mutableSetOf<String>()
        val mangas = mutableListOf<SManga>()

        for (a in document.select("a[href]")) {
            val absUrl = a.absUrl("href")
            val path = absUrl.removePrefix(baseUrl)
            if (!PATH_PATTERN.matches(path) || path in seen || path.contains("browse")) continue
            seen.add(path)

            val card = a.closest("div.group") ?: a
            val title = card.selectFirst("h3")?.text()
                ?: a.attr("aria-label").trim().takeIf { it.isNotEmpty() }
                ?: a.text().takeIf { it.isNotEmpty() }
                ?: continue

            val manga = SManga.create().apply {
                setUrlWithoutDomain(absUrl)
                this.title = title
                thumbnail_url = card.selectFirst("img[src]")?.absUrl("src")
            }
            mangas.add(manga)
        }

        val hasNextPage = document.selectFirst("a[href*=\"page=${page + 1}\"]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val VALID_TYPES = setOf("manhwa", "manga", "western", "comic")
        private val PATH_PATTERN = Regex("""^/(?:manhwa|manga|western|comic)/([^/]+)$""")
        private val COMIC_ID_REGEX = Regex("""comicId[^:]*:(\d+)""")
    }
}
