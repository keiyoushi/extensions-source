package eu.kanade.tachiyomi.extension.uk.comixtopia

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
import keiyoushi.utils.array
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class ComixTopia : KeiSource() {

    private val apiUrl get() = "https://supa.${baseUrl.toHttpUrl().host}/rest/v1"

    private val apiHeaders: Headers get() = headersBuilder()
        .set("apikey", API_KEY)
        .set("authorization", "Bearer $API_KEY")
        .set("x-client-info", "supabase-ssr/0.10.3 createBrowserClient")
        .build()

    private val apiCountHeaders: Headers get() = headersBuilder()
        .set("apikey", API_KEY)
        .set("authorization", "Bearer $API_KEY")
        .set("x-client-info", "supabase-ssr/0.10.3 createBrowserClient")
        .set("Prefer", "count=exact")
        .build()

    // =========================== Popular ============================
    override suspend fun getPopularManga(page: Int): MangasPage = getComixList(page, "metadata(views).desc")

    // =========================== Latest ============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = getComixList(page, "updated_at.desc")

    // =========================== Search ============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getComixList(page, "", query, filters)

    // =========================== Search Utilities ============================
    private suspend fun getComixList(page: Int, sortBy: String, query: String = "", filters: FilterList? = null): MangasPage {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("comics")
            addQueryParameter("select", "slug,ukrainian_name,cover,authors:authors!inner(id),genres:genres!inner(id),publishers:publishers!inner(id),metadata:comics_metadata!inner(state,views)")
            addQueryParameter("metadata.state", "eq.approved")
            if (!query.isBlank()) addQueryParameter("or", "(ukrainian_name.ilike.*$query*,original_name.ilike.*$query*)")

            filters?.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.selected?.let { addQueryParameter("genres.id", "in.(${it.joinToString(",")})") }
                    is AuthorsFilter -> filter.selected?.let { addQueryParameter("authors.id", "in.(${it.joinToString(",")})") }
                    is PublishersFilter -> filter.selected?.let { addQueryParameter("publishers.id", "in.(${it.joinToString(",")})") }
                    is ComicsStatus -> filter.selected?.let { addQueryParameter("comic_status", "eq.$it") }
                    is AgeLimit -> filter.selected?.let { addQueryParameter("age_limit", "in.(${it.joinToString(",")})") }
                    is OrderBy -> {
                        addQueryParameter("order", "${filter.selected}.${filter.order}")
                        if (filter.selected == "issue_count") addQueryParameter("issue_count", "gt.0")
                    }
                    else -> {}
                }
            }

            if (filters == null) addQueryParameter("order", sortBy)

            addQueryParameter("limit", PAGINATION.toString())
            addQueryParameter("offset", ((page - 1) * PAGINATION).toString())
        }.build()

        return client.get(url, apiCountHeaders).use { response ->
            val contentRange = response.header("content-range")?.substringAfter("/")?.toIntOrNull() ?: 0
            val hasNextPage = (page * 10) < contentRange
            val manga = response.parseAs<List<TitlesList>>().map { it.toSManga() }
            MangasPage(manga, hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host == baseUrl.toHttpUrl().host && url.pathSegments[0] == "titles" && url.pathSegments[1].length > 1) {
            val tmpManga = SManga.create().apply {
                this.url = url.pathSegments[1]
            }

            return fetchMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
        }

        return null
    }

    // =========================== Manga ============================
    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = manga.url

        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("comics")
            addQueryParameter(
                "select",
                "slug,original_name,ukrainian_name,release_year,comic_status,age_limit,description,cover," +
                    "authors:authors!inner(name)," +
                    "publishers:publishers!inner(name)," +
                    "genres:genres!inner(name)," +
                    "votes:votes!inner(rating)," +
                    "issues:issues!inner(id,issue_no,translator,created_at,image_list,metadata:issues_metadata!inner(state))",
            )
            addQueryParameter("slug", "eq.$mangaUrl")
        }.build()

        val data = client.get(url, apiHeaders).parseAs<List<MangaFull>>().first()
        val newManga = data.toSManga()
        val newChapters = data.toSChapters(mangaUrl).sortedWith(
            compareByDescending<SChapter> { it.chapter_number }.thenByDescending { it.date_upload },
        )

        return SMangaUpdate(newManga, newChapters)
    }

    // =========================== Pages ============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/titles/${chapter.memo["mangaId"]!!.string}/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = chapter.memo["pages"]?.array
        if (pages.isNullOrEmpty()) throw Exception("Не вдалося знайти зображення. Перевірте розділ у WebView.")

        return pages.mapIndexed { index, element ->
            Page(index, imageUrl = element.string)
        }
    }

    // =========================== Filters ============================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async {
            client.get("$apiUrl/genres?select=id,name", apiHeaders).use {
                it.parseAs<List<IdNameDto>>().sortedBy { dto -> dto.name }.map { dto -> dto.name to dto.id.toString() }
            }
        }
        val authors = async {
            val data = client.get("$apiUrl/authors?select=id,name", apiHeaders).use {
                it.parseAs<List<IdNameDto>>()
            }
            // 530 Authors
            withContext(Dispatchers.Default) {
                data.sortedBy { dto -> dto.name }.map { dto -> dto.name to dto.id.toString() }
            }
        }
        val publishers = async {
            client.get("$apiUrl/publishers?select=id,name", apiHeaders).use {
                it.parseAs<List<IdNameDto>>().sortedBy { dto -> dto.name }.map { dto -> dto.name to dto.id.toString() }
            }
        }

        FiltersDto(
            genres = genres.await(),
            authors = authors.await(),
            publishers = publishers.await(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters.add(OrderBy())

        data?.parseAs<FiltersDto>()?.let {
            if (it.genres?.isNotEmpty() == true) filters.add(GenreFilter(it.genres))
            if (it.authors?.isNotEmpty() == true) filters.add(AuthorsFilter(it.authors))
            if (it.publishers?.isNotEmpty() == true) filters.add(PublishersFilter(it.publishers))
        }

        filters.addAll(
            listOf(
                AgeLimit(),
                ComicsStatus(),
            ),
        )

        return FilterList(filters)
    }

    companion object {
        private const val API_KEY = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzdXBhYmFzZSIsImlhdCI6MTc2Mzk4NTk2MCwiZXhwIjo0OTE5NjU5NTYwLCJyb2xlIjoiYW5vbiJ9.mYabtnjgn71ekivrvtG86uRTUBgcUOorS9sHlT--Ats"
        private const val PAGINATION = 10
    }
}
