package eu.kanade.tachiyomi.extension.pt.sssscanlator

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

@Source
abstract class YomuComics : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "popular")
            .addQueryParameter("type", DEFAULT_TYPE)
            .build()

        return GET(url, bibliotecaHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/library".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "recent")
            .addQueryParameter("type", DEFAULT_TYPE)
            .build()

        return GET(url, bibliotecaHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue ?: DEFAULT_TYPE
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue ?: DEFAULT_STATUS
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: DEFAULT_SORT

        val url = "$baseUrl/api/library".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            addQueryParameter("sort", sort)
            addQueryParameter("type", type)

            if (genre.isNotBlank()) {
                addQueryParameter("genre", genre)
            }

            if (status != DEFAULT_STATUS) {
                addQueryParameter("status", status)
            }

            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
        }.build()

        return GET(url, bibliotecaHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseLibraryResponse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga = parseSeriesPage(response).manga

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBefore('?')

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> = parseSeriesPage(response).chapters

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterPageUrl = getChapterUrl(chapter)

        val requestHeaders = headers.newBuilder()
            .set("Referer", chapterPageUrl)
            .set("RSC", "1")
            .build()

        return GET(chapterPageUrl, requestHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val matches = mutableListOf<JsonElement>()
        response.extractNextJs<JsonElement> { element ->
            val chapter = (element as? JsonObject)?.get("chapter") as? JsonObject
            if (chapter?.get("imagens_lista") is JsonArray) matches.add(element)
            false
        }

        val chapterArrays = matches.map { ((it as JsonObject)["chapter"] as JsonObject)["imagens_lista"] as JsonArray }
        val data = selectRealArray(chapterArrays)?.let { matches[it].parseAs<ChapterPageDto>() }

        if (data != null && data.chapter.images.isNotEmpty()) {
            return data.chapter.images.mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }

        error("Nenhuma pagina encontrada para este capitulo")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val requestHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, requestHeaders)
    }

    // Filters

    override fun getFilterList() = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // Utils

    private fun parseLibraryResponse(response: Response): MangasPage {
        val resultString = response.body.string()
        val pagination = resultString.parseAs<LibraryResponseDto>().pagination

        // mangas field name changes frequently
        val allArrays = resultString
            .parseAs<JsonElement>()
            .jsonObject.values
            .mapNotNull { value ->
                when (value) {
                    is JsonArray -> value
                    is JsonPrimitive -> value.contentOrNull?.let { base64Str ->
                        runCatching {
                            Base64.decode(base64Str, Base64.DEFAULT)
                                .toString(Charsets.UTF_8)
                                .parseAs<JsonArray>()
                        }.getOrNull()
                    }
                    else -> null
                }
            }

        val rIndex = selectRealArray(allArrays)
        val mangasList = allArrays[rIndex!!].map { it.parseAs<LibraryMangaDto>() }

        val mangas = mangasList.filter { it.type != "novel" }.map(LibraryMangaDto::toSManga)
        val hasNextPage = pagination.page < pagination.totalPages
        return MangasPage(mangas, hasNextPage)
    }

    private fun parseSeriesPage(response: Response): SeriesPageData {
        val mangaSlug = response.request.url.pathSegments.lastOrNull().orEmpty()
        val document = response.asJsoup()
        val payload = extractSeriesPayload(document, mangaSlug)

        val titleElement = document.selectFirst("h1")
        val title = titleElement!!.text()
        val badgeTexts = extractBadgeTexts(titleElement)
        val statusText = badgeTexts.firstOrNull(::isStatusBadge)
        val genres = badgeTexts.filterNot(::isStatusBadge)

        val manga = SManga.create().apply {
            this.title = title
            thumbnail_url = payload.coverImage?.takeUnless(String::isBlank)
            description = payload.description?.takeUnless(String::isBlank)
            author = payload.author?.takeUnless(String::isBlank)
            artist = payload.artist?.takeUnless(String::isBlank)
            genre = genres.joinToString().takeUnless(String::isBlank)
            status = parseStatus(statusText)
            url = "/obra/$mangaSlug"
        }

        val chapters = payload.chapters.map { chapter ->
            chapter.toSChapter(mangaSlug)
        }

        return SeriesPageData(manga, chapters)
    }

    private class SeriesPageData(
        val manga: SManga,
        val chapters: List<SChapter>,
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val DEFAULT_TYPE = "all"
        const val DEFAULT_STATUS = "all"
        const val DEFAULT_SORT = "popular"
    }

    private val bibliotecaHeaders by lazy {
        headers.newBuilder()
            .set("Referer", "$baseUrl/biblioteca")
            .build()
    }
}
