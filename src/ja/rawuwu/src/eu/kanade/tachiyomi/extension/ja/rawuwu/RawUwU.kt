package eu.kanade.tachiyomi.extension.ja.rawuwu

import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.ChapterPageResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.MangaDetailResponseDto
import eu.kanade.tachiyomi.extension.ja.rawuwu.dto.RawUwUResponseDto
import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import kotlin.time.Instant

@Source
abstract class RawUwU : KeiSource() {
    override val supportsFilterFetching = true

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/spa/genre/all".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "most_viewed")
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseMangasPage(response)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/spa/latest-manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        val response = client.get(url)
        return parseMangasPage(response)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addPathSegment("search").addQueryParameter("query", query)
        } else {
            filters.forEach { filter ->
                if (filter is UriFilter) {
                    filter.addToUri(url)
                } else if (filter is GenreFilter) {
                    url.addPathSegment("genre")
                    url.addPathSegment(filter.values[filter.state].path)
                }
            }
        }
        url.addQueryParameter("page", page.toString())
        val response = client.get(url.build())
        return parseMangasPage(response)
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val result = response.parseAs<RawUwUResponseDto>()
        val mangas = result.mangaList?.map { manga ->
            SManga.create().apply {
                url = manga.mangaId.toString()
                title = manga.mangaName
                thumbnail_url = manga.mangaCoverImg
            }
        } ?: emptyList()

        val hasNextPage = result.pagi?.button?.next?.let { it > 0 } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/raw/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() !in setOf("raw", "read")) return null

        val mangaId = url.pathSegments.getOrNull(1) ?: return null
        val result = client.get("$baseUrl/spa/manga/$mangaId").parseAs<MangaDetailResponseDto>()

        return parseMangaDetails(result).apply {
            this.url = mangaId
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = client.get("$baseUrl/spa/manga/${manga.url}").parseAs<MangaDetailResponseDto>()
        return SMangaUpdate(parseMangaDetails(result), parseChapterList(result))
    }

    private fun parseMangaDetails(result: MangaDetailResponseDto): SManga = SManga.create().apply {
        val detail = result.detail ?: throw Exception("Could not find manga details")
        title = detail.mangaName
        thumbnail_url = detail.mangaCoverImgFull
            ?: detail.mangaCoverImg

        val descriptionText = detail.mangaDescription
        val altName = detail.mangaOthersName
        description = buildString {
            if (!descriptionText.isNullOrBlank()) append(descriptionText)

            if (!altName.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")

                append("Alternative Names: ")
                altName.split(",").forEach { name ->
                    append("\n - ${name.trim()}")
                }
            }
        }

        author = result.authors?.joinToString { it.authorName }?.ifEmpty { null }
        genre = result.tags?.joinToString { it.tagName.toGenreName() }?.ifEmpty { null }

        status = when (detail.mangaStatus) {
            true -> SManga.COMPLETED
            false -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun parseChapterList(result: MangaDetailResponseDto): List<SChapter> {
        val mangaId = result.detail?.mangaId ?: throw Exception("Could not find chapters")
        val chaptersArray = result.chapters ?: return emptyList()

        return chaptersArray.map { chapter ->
            SChapter.create().apply {
                val formattedNum = chapter.chapterNumber!!.toString().removeSuffix(".0")
                url = "/read/$mangaId/chapter-$formattedNum"
                val title = chapter.chapterTitle?.trim()
                name = if (!title.isNullOrBlank()) "Ch. $formattedNum - $title" else "Chapter $formattedNum"
                date_upload = Instant.tryParse(chapter.chapterDatePublished)
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = baseUrl.toHttpUrl().resolve(chapter.url)!!.pathSegments
        val mangaId = segments[1]
        val chapterNum = segments[2].removePrefix("chapter-")

        val result = client.get("$baseUrl/spa/manga/$mangaId/$chapterNum").parseAs<ChapterPageResponseDto>()

        val chapterDetail = result.chapterDetail ?: throw Exception("Could not find chapter detail")
        val serverUrl = chapterDetail.server ?: throw Exception("Could not server url")
        val htmlContent = chapterDetail.chapterContent ?: throw Exception("Could not find chapter pages")

        val document = Jsoup.parseBodyFragment(htmlContent, serverUrl)
        return document.select("img").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src"))
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Array<Genre>>() ?: emptyArray()
        return FilterList(
            Filter.Header("Filters are ignored when using text search."),
            StatusFilter(),
            SortFilter(),
            GenreFilter(genres),
        )
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/genre/all").asJsoup()
        val genres = document.select(".genre-list a[id]").map {
            Genre(it.attr("title").toGenreName(), it.id())
        }
        return genres.toJsonElement()
    }

    private fun String.toGenreName() = split(' ')
        .joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }
}
