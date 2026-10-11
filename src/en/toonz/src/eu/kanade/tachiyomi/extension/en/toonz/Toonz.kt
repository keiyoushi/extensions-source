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
import keiyoushi.utils.attrOrNull
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
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Toonz : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 2, period = 1.seconds)
        .addCookie("reader_prefs" to "%7B%22rating%22%3A%22pornographic%22%7D")

    private val isToonx get() = name == "Toonx"

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(listingUrl("comics", "popular", page), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(listingUrl("comics", "latest", page), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addQueryParameter("q", query.trim())
                .addQueryParameter("page", page.toString())
                .build()
            return getMangaList(url, page)
        }

        val sort = filters.firstInstanceOrNull<SortFilter>()?.getValue() ?: "popular"

        val genre = filters.firstInstanceOrNull<GenreFilter>()?.getValue().orEmpty()
        if (genre.isNotBlank()) {
            return getMangaList(listingUrl("genre/$genre", sort, page), page)
        }

        val catalog = filters.firstInstanceOrNull<CatalogFilter>()?.getValue() ?: "comics"
        return getMangaList(listingUrl(catalog, sort, page), page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val type = url.pathSegments.firstOrNull() ?: return null
        if (type !in (if (isToonx) TOONX_TYPES else TOONZ_TYPES)) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply {
            this.url = mangaPath(type, slug)
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
            listOfNotNull(
                CatalogFilter().takeUnless { isToonx },
                SortFilter(),
                Filter.Separator(),
                GenreFilter(genreOptions),
            ),
        )
    }

    private fun listingUrl(path: String, sort: String, page: Int): HttpUrl = baseUrl.toHttpUrl().newBuilder()
        .addPathSegments(path)
        .addQueryParameter("sort", sort)
        .addQueryParameter("page", page.toString())
        .build()

    private suspend fun getMangaList(url: HttpUrl, page: Int): MangasPage {
        val document = client.get(url).asDocument()
        val mangas = document.select("img[data-cover]").map { img ->
            SManga.create().apply {
                val link = img.closest("a[href], :has(> a[href])")!!.selectFirst("a[href]")!!.absUrl("href").toHttpUrl()
                this.url = mangaPath(link.pathSegments[0], link.pathSegments[1])
                title = img.attrOrNull("alt")!!
                thumbnail_url = img.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst("a[href*=\"page=${page + 1}\"]") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Next.js streams late parts of the page as hidden chunks that client JS moves into <template> placeholders.
    private fun Response.asDocument(): Document = asJsoup().apply {
        select("div[hidden][id^=S:]").forEach { chunk ->
            val placeholder = getElementById("P:${chunk.id().removePrefix("S:")}") ?: return@forEach
            placeholder.parent()!!.insertChildren(placeholder.siblingIndex(), chunk.childNodes())
            placeholder.remove()
            chunk.remove()
        }
    }

    // Toonz links /manhua/ for /manhwa/; Toonx links /comic/ for /adult/
    private fun mangaPath(type: String, slug: String): String = when (type) {
        "manhua" -> "/manhwa/$slug"
        "comic" -> "/adult/$slug"
        else -> "/$type/$slug"
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private val TOONZ_TYPES = setOf("manhwa", "manhua", "manga", "western")
        private val TOONX_TYPES = setOf("comic", "adult")
        private val COMIC_ID_REGEX = Regex("""comicId[^:]*:(\d+)""")
    }
}
