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
import keiyoushi.utils.firstInstanceOrNull
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
    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter.POPULAR))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter.LATEST))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/spa".toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addPathSegment("search").addQueryParameter("query", query)
        } else {
            filters.filterIsInstance<UriFilter>().forEach { it.addToUri(url) }
            val genre = filters.firstInstanceOrNull<GenreFilter>() ?: GenreFilter()
            url.addPathSegment("genre").addPathSegment(genre.values[genre.state].path)
        }
        url.addQueryParameter("page", page.toString())
        val response = client.get(url.build())
        return parseMangasPage(response)
    }

    private fun parseMangasPage(response: Response): MangasPage {
        val result = response.parseAs<RawUwUResponseDto>()
        val mangas = result.mangaList.map { manga ->
            SManga.create().apply {
                url = manga.mangaId.toString()
                title = manga.mangaName
                thumbnail_url = manga.mangaCoverImg
            }
        }

        val hasNextPage = result.pagi.button.next > 0
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
        val detail = result.detail
        title = detail.mangaName
        thumbnail_url = detail.mangaCoverImg

        description = buildString {
            detail.mangaDescription?.let(::append)
            if (isNotEmpty()) append("\n\n")

            append("Alternative Names: ")
            detail.mangaOthersName.split(",").forEach { name ->
                append("\n - ${name.trim()}")
            }
        }

        author = result.authors.joinToString { it.authorName }.ifEmpty { null }
        genre = result.tags.joinToString { it.tagName.toGenreName() }.ifEmpty { null }

        status = if (detail.mangaStatus) SManga.COMPLETED else SManga.ONGOING
    }

    private fun parseChapterList(result: MangaDetailResponseDto): List<SChapter> {
        val mangaId = result.detail.mangaId

        return result.chapters.map { chapter ->
            SChapter.create().apply {
                val chapterNumber = chapter.chapterNumber
                val formattedNum = chapterNumber.toString().removeSuffix(".0")
                url = "/read/$mangaId/chapter-$formattedNum"
                chapter_number = chapterNumber
                name = chapter.chapterTitle?.let { "Ch. $formattedNum - $it" } ?: "Chapter $formattedNum"
                date_upload = Instant.tryParse(chapter.chapterDatePublished)
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val segments = baseUrl.toHttpUrl().resolve(chapter.url)!!.pathSegments
        val mangaId = segments[1]
        val chapterNum = segments[2].removePrefix("chapter-")

        val result = client.get("$baseUrl/spa/manga/$mangaId/$chapterNum").parseAs<ChapterPageResponseDto>()

        val chapterDetail = result.chapterDetail
        val document = Jsoup.parseBodyFragment(chapterDetail.chapterContent, chapterDetail.server)
        return document.select("img").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src"))
        }
    }

    override val supportsFilterFetching = true

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
