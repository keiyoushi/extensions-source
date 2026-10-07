package eu.kanade.tachiyomi.extension.pt.nexusmangas

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

@Source
abstract class NexusMangas : KeiSource() {

    private val apiUrl = "https://$API_HOST/rest/v1"

    private val functionsUrl = "https://$API_HOST/functions/v1"

    private val mediaUrl = "$functionsUrl/read-public-media".toHttpUrl()

    private val pageInterceptor = Interceptor(::renewExpiredPage)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        .addInterceptor(::authorizeApi)
        .addInterceptor(pageInterceptor)
        .rateLimit(3) { it.host == API_HOST && it.encodedPath != mediaUrl.encodedPath }

    private val pageClient by lazy {
        client.newBuilder()
            .apply { interceptors().removeAll { it === pageInterceptor || it.javaClass.simpleName == "CloudflareInterceptor" } }
            .build()
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept", "application/json")

    private val readerHeaders: Headers
        get() = headersBuilder()
            .set("x-nexus-client", "reader-v3")
            .build()

    private fun authorizeApi(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != API_HOST) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("apikey", ANON_KEY)
                .header("Authorization", "Bearer $ANON_KEY")
                .build(),
        )
    }

    private fun renewExpiredPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fragment = request.url.fragment
        if (fragment == null || request.url.host == API_HOST) return chain.proceed(request)

        val response = pageClient.newCall(request).execute()
        val index = fragment.substringAfter('/').toIntOrNull()
        if (response.code != 403 || index == null) return response

        response.close()
        val readRequest = Request.Builder()
            .url("$functionsUrl/read-chapter")
            .headers(readerHeaders)
            .post(ReadChapterRequestDto(fragment.substringBefore('/')).toJsonRequestBody())
            .build()
        val pageUrl = client.newCall(readRequest).execute()
            .parseAs<ReadChapterDto>()
            .pageUrl(index)

        return pageClient.newCall(request.newBuilder().url(pageUrl).build()).execute()
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchWorks(page, order = POPULAR_ORDER)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchWorks(page, order = LATEST_ORDER)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = fetchWorks(
        page = page,
        order = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: POPULAR_ORDER,
        search = query,
        type = filters.firstInstanceOrNull<TypeFilter>()?.selectedValue,
        status = filters.firstInstanceOrNull<StatusFilter>()?.selectedValue,
        demographic = filters.firstInstanceOrNull<DemographicFilter>()?.selectedValue,
        genre = filters.firstInstanceOrNull<GenreFilter>()?.selectedValue,
    )

    private suspend fun fetchWorks(
        page: Int,
        order: String,
        search: String? = null,
        type: String? = null,
        status: String? = null,
        demographic: String? = null,
        genre: String? = null,
    ): MangasPage {
        val genreJoin = if (genre.isNullOrBlank()) "" else ",work_genres!inner(genres!inner(name))"

        val url = "$apiUrl/works".toHttpUrl().newBuilder()
            .addQueryParameter("select", "$LIST_COLUMNS$genreJoin")
            .addQueryParameter("order", "$order,id.desc")
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .apply {
                search?.sanitized()?.let {
                    addQueryParameter("or", "(title.ilike.*$it*,alternative_title.ilike.*$it*)")
                }
                type?.takeIf(String::isNotBlank)?.let { addQueryParameter("type", "eq.$it") }
                status?.takeIf(String::isNotBlank)?.let { addQueryParameter("status", "eq.$it") }
                demographic?.takeIf(String::isNotBlank)?.let { addQueryParameter("demographic", "eq.$it") }
                genre?.takeIf(String::isNotBlank)?.let {
                    addQueryParameter("work_genres.genres.name", "eq.$it")
                }
            }
            .build()

        val works = client.get(url).parseAs<List<WorkDto>>()

        return MangasPage(works.map { it.toSManga(mediaUrl) }, works.size == PAGE_SIZE)
    }

    private fun String.sanitized(): String? = replace(FILTER_SYNTAX_REGEX, " ").trim().takeIf(String::isNotBlank)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "obra") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val manga = SManga.create().apply { this.url = slug }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
        val url = "$apiUrl/works".toHttpUrl().newBuilder()
            .addQueryParameter("select", DETAIL_COLUMNS)
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("limit", "1")
            .build()

        val work = client.get(url).parseAs<List<WorkDto>>().firstOrNull()
            ?: throw IOException("Obra não encontrada")

        return SMangaUpdate(manga = work.toSManga(mediaUrl), chapters = work.chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/obra/${manga.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, number) = chapter.url.split('/').takeLast(2)

        val url = "$apiUrl/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,works!inner(slug)")
            .addQueryParameter("works.slug", "eq.$slug")
            .addQueryParameter("number", "eq.$number")
            .addQueryParameter("limit", "1")
            .build()

        val chapterId = client.get(url).parseAs<List<ChapterIdDto>>().firstOrNull()?.id
            ?: throw IOException("Capítulo não encontrado")

        return client.post("$functionsUrl/read-chapter", readerHeaders, ReadChapterRequestDto(chapterId).toJsonRequestBody())
            .parseAs<ReadChapterDto>()
            .toPageList(chapterId)
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/genres".toHttpUrl().newBuilder()
            .addQueryParameter("select", "name")
            .addQueryParameter("order", "name.asc")
            .addQueryParameter("limit", "200")
            .build()

        val genres = client.get(url).parseAs<List<GenreDto>>().map(GenreDto::name)

        return FilterData(genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<FilterData>()?.genres

        return FilterList(
            listOfNotNull(
                SortFilter(),
                TypeFilter(),
                StatusFilter(),
                DemographicFilter(),
                genres?.takeIf(List<String>::isNotEmpty)?.let(::GenreFilter),
            ),
        )
    }

    companion object {
        private const val API_HOST = "supabase.nexusmangas.com"
        private const val PAGE_SIZE = 30
        private const val POPULAR_ORDER = "avg_rating.desc.nullslast"
        private const val LATEST_ORDER = "updated_at.desc"
        private const val LIST_COLUMNS = "slug,title,cover_url,status,type"
        private const val DETAIL_COLUMNS =
            "slug,title,description,cover_url,alternative_title,status,type,author,artist," +
                "demographic,scan:scans(name),work_genres(genres(name))," +
                "chapters(number,title,published_at)"
        private const val ANON_KEY =
            "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9." +
                "eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc4NzgwMjAwMCwiZXhwIjo0OTQzNDc1NjAwLCJyb2xlIjoiYW5vbiJ9." +
                "Cnl8Jw2DeKe84OAkmJYfO33xlcZsw0TC2Nw_il0tpRs"
        private val FILTER_SYNTAX_REGEX = Regex("""[,()*]""")
    }
}
