package eu.kanade.tachiyomi.extension.en.lusttoon

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class LustToon : KeiSource() {

    private val apiUrl = "https://back.lustoon.com"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor { chain ->
            val request = chain.request()
            if (request.url.scheme == "http") {
                val newUrl = request.url.newBuilder().scheme("https").build()
                chain.proceed(request.newBuilder().url(newUrl).build())
            } else {
                chain.proceed(request)
            }
        }
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
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

        val resp = client.get(url).parseAs<SearchResponseDto>()
        return MangasPage(resp.mangas, resp.hasNext)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page == 1) {
            val rscHeaders = headers.newBuilder()
                .add("RSC", "1")
                .build()
            val response = client.get(baseUrl, rscHeaders)
            val home = response.extractNextJs<HomeDto> { element ->
                element is JsonObject && "comics" in element && element["comics"] is JsonArray
            }
            if (home != null && home.mangas.isNotEmpty()) {
                return MangasPage(home.mangas, true)
            }
        }

        val url = "$apiUrl/filtrar".toHttpUrl().newBuilder()
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("orderBy", "3")
            .addQueryParameter("sort", "desc")
            .addQueryParameter("gendersId", "")
            .addQueryParameter("origin", "")
            .addQueryParameter("state", "")
            .addQueryParameter("loading", "true")
            .build()

        val resp = client.get(url).parseAs<SearchResponseDto>()
        return MangasPage(resp.mangas, resp.hasNext)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$apiUrl/home/buscar".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .build()
            val items = client.get(url).parseAs<List<SearchItemDto>>()
            return MangasPage(items.filter { it.slug != null }.map { it.toSManga() }, false)
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

        val resp = client.get(url).parseAs<SearchResponseDto>()
        return MangasPage(resp.mangas, resp.hasNext)
    }

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = runCatching {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        if (url.pathSegments.firstOrNull() != "comic" || slug.isBlank()) return null

        val manga = SManga.create().apply {
            this.url = "/comic/$slug"
        }
        getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }.getOrNull()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val rscHeaders = headers.newBuilder()
            .add("RSC", "1")
            .build()
        val response = client.get(getMangaUrl(manga), rscHeaders)
        val serie = response.extractNextJs<SerieDto> { element ->
            element is JsonObject && "slug" in element && "chapters" in element
        } ?: throw Exception("Failed to find valid series data")

        val mangaSlug = serie.slug ?: manga.url.substringAfterLast("/")

        return SMangaUpdate(
            manga = serie.toSManga(),
            chapters = serie.chapters?.filter { it.slug != null }?.map { it.toSChapter(mangaSlug) } ?: emptyList(),
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val document = response.asJsoup()
        val pageches = document.extractNextJs<PagechesDto> { element ->
            element is JsonObject && "urlImg" in element && "chapterId" in element
        }

        val images = pageches?.images?.ifEmpty { null }
            ?: imageUrlRegex.findAll(document.html())
                .map { it.value }
                .filter { it.contains("/serie/") }
                .toList()

        return images
            .map { it.replace("http://", "https://") }
            .distinct()
            .filterNot { it.contains("brakeout") }
            .mapIndexed { i, url ->
                Page(i, imageUrl = url)
            }
    }

    companion object {
        private val imageUrlRegex = Regex("""https?://media\.lustoon\.com/file/[^"\s']+\.(?:jpg|jpeg|png|webp|avif)""", RegexOption.IGNORE_CASE)
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(),
        Filter.Separator(),
        TypeFilter(),
        StatusFilter(),
        GenreFilter(),
    )
}
