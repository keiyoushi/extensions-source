package eu.kanade.tachiyomi.extension.all.manhwa18net

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.Jsoup
import kotlin.time.Instant

@Source
abstract class Manhwa18Net : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3)

    private fun extractPageDto(response: Response): PageDto {
        val document = response.asJsoup()
        val app = document.selectFirst("#app")
            ?: throw Exception("Could not find #app element")
        val data = app.attr("data-page")
        if (data.isBlank()) throw Exception("data-page attribute is empty")
        return data.parseAs<PageDto>()
    }

    // ============================================================
    // LISTS
    // ============================================================

    override suspend fun getPopularManga(page: Int): MangasPage = parseList(client.get("$baseUrl/manga-list?sort=top&page=$page"))

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseList(client.get("$baseUrl/manga-list?sort=update&page=$page"))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val builder = if (query.isNotEmpty()) {
            "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
        } else {
            "$baseUrl/manga-list".toHttpUrl().newBuilder()
        }

        builder.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> builder.addQueryParameter("sort", filter.toUriPart())

                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            builder.addQueryParameter(status.uriParam, "1")
                        }
                    }
                }

                else -> {}
            }
        }

        return parseList(client.get(builder.build()))
    }

    private suspend fun parseList(response: Response): MangasPage {
        val props = extractPageDto(response).props

        val listing = props.paginate
            ?: props.popularManga
            ?: props.mangas
            ?: props.latestManhwaMain
            ?: throw Exception("No manga listing found in response")

        val mangas = listing.data.map { manga ->
            SManga.create().apply {
                title = manga.name
                url = "/manga/${manga.slug}"
                thumbnail_url = fixImageUrl(manga.coverUrl ?: manga.thumbUrl)
            }
        }

        return MangasPage(mangas, listing.nextPageUrl != null)
    }

    // ============================================================
    // FILTERS
    // ============================================================

    override fun getFilterList(data: JsonElement?) = getFilters()

    // ============================================================
    // DETAILS + CHAPTER LIST
    // ============================================================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val props = extractPageDto(client.get(getMangaUrl(manga))).props

        if (fetchDetails && props.manga == null) throw Exception("Manga details not found")
        if (fetchChapters && props.chapters == null) throw Exception("Chapters not found")

        val newManga = props.manga?.let { mangaDto ->
            SManga.create().apply {
                title = mangaDto.name

                description = mangaDto.pilot?.let { Jsoup.parse(it).text() }
                    ?: mangaDto.description?.let { Jsoup.parse(it).text() }

                thumbnail_url = fixImageUrl(mangaDto.coverUrl ?: mangaDto.thumbUrl)

                genre = mangaDto.genres?.joinToString { it.name }

                author = mangaDto.artists?.joinToString { it.name }?.ifEmpty { null }

                artist = author

                status = when (mangaDto.statusId) {
                    0 -> SManga.ONGOING
                    1, 2 -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        } ?: manga

        val mangaSlug = props.manga?.slug ?: manga.url.substringAfterLast("/")
        val newChapters = props.chapters?.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                url = "/manga/$mangaSlug/${chapter.slug}"
                date_upload = Instant.tryParse(chapter.createdAt)
            }
        } ?: chapters

        return SMangaUpdate(newManga, newChapters)
    }

    // ============================================================
    // PAGE LIST
    // ============================================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val props = extractPageDto(client.get(getChapterUrl(chapter))).props
        val chapterContent = props.chapterContent
            ?: throw Exception("Chapter content not found")

        val contentDoc = Jsoup.parse(chapterContent)
        val images = contentDoc.select("img")

        return images.mapIndexedNotNull { index, img ->
            val src = img.attr("src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("data-lazy-src") }

            fixImageUrl(src)?.let { Page(index, "", it) }
        }
    }

    // ============================================================
    // UTIL
    // ============================================================

    private fun fixImageUrl(url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("http") -> url
        url.startsWith("/") -> baseUrl + url
        else -> "$baseUrl/$url"
    }
}
