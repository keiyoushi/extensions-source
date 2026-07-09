package eu.kanade.tachiyomi.extension.en.lusttoon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Source
abstract class LustToon : HttpSource() {

    private val apiUrl = "https://back.lustoon.com"

    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            if (request.url.scheme == "http") {
                val newUrl = request.url.newBuilder().scheme("https").build()
                chain.proceed(request.newBuilder().url(newUrl).build())
            } else {
                chain.proceed(request)
            }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/filtrar".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("orderBy", "6")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("gendersId", "")
            .addQueryParameter("origin", "")
            .addQueryParameter("state", "")
            .addQueryParameter("loading", "true")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val resp = response.parseAs<SearchResponseDto>()
        return MangasPage(resp.mangas, resp.hasNext)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/filtrar".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("orderBy", "3")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("gendersId", "")
            .addQueryParameter("origin", "")
            .addQueryParameter("state", "")
            .addQueryParameter("loading", "true")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/home/buscar".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            return GET(url, headers)
        }

        val sortFilter = filters.firstInstanceOrNull<SortFilter>()

        val url = "$apiUrl/filtrar".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("loading", "true")
            .addQueryParameter("orderBy", sortFilter?.selected ?: "1")
            .addQueryParameter("sort", if (sortFilter?.state?.ascending == true) "asc" else "desc")
            .addQueryParameter("gendersId", filters.firstInstanceOrNull<GenreFilter>()?.selected ?: "")
            .addQueryParameter("origin", filters.firstInstanceOrNull<TypeFilter>()?.selected ?: "")
            .addQueryParameter("state", filters.firstInstanceOrNull<StatusFilter>()?.selected ?: "")
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        if (url.contains("/home/buscar")) {
            val items = response.parseAs<List<SearchItemDto>>()
            return MangasPage(items.filter { it.slug != null }.map { it.toSManga() }, false)
        }

        return popularMangaParse(response)
    }

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headersBuilder().add("RSC", "1").build())

    override fun mangaDetailsParse(response: Response): SManga {
        val serie = response.extractNextJs<SerieDto> { element ->
            element is JsonObject && "slug" in element && "chapters" in element
        } ?: throw Exception("Failed to find valid series data")

        return serie.toSManga()
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val serie = response.extractNextJs<SerieDto> { element ->
            element is JsonObject && "slug" in element && "chapters" in element
        } ?: return emptyList()

        val mangaSlug = serie.slug ?: return emptyList()

        return serie.chapters?.filter { it.slug != null }?.map { chapter ->
            chapter.toSChapter(mangaSlug).apply {
                chapter.createdAt?.substringBefore("+")?.substringBefore("Z")?.let {
                    date_upload = dateFormat.tryParse(it)
                }
            }
        } ?: emptyList()
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("div.max-w-4xl img")

        return images.mapIndexed { i, img ->
            Page(i, imageUrl = img.absUrl("src").replace("http://", "https://"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}
