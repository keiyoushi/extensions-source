package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class LavibitWindroom : KeiSource() {

    private val apiUrl = "https://103-77-242-114.sslip.io/rest/v1"
    private val apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyAgCiAgICAicm9sZSI6ICJhbm9uIiwKICAgICJpc3MiOiAic3VwYWJhc2UtZGVtbyIsCiAgICAiaWF0IjogMTY0MTc2OTIwMCwKICAgICJleHAiOiAxNzk5NTM1NjAwCn0.dc_X5iR_VP_qT0zsiyj_I_OZ2T9FtRU2BBNWN8Bu4GE"

    private val apiHeaders: Headers
        get() = headersBuilder()
            .set("apikey", apiKey)
            .set("Authorization", "Bearer $apiKey")
            .set("Accept-Profile", "public")
            .build()

    private suspend fun apiGet(url: HttpUrl): Response = client.get(url, apiHeaders)

    private fun apiUrlBuilder() = "$apiUrl/series".toHttpUrl().newBuilder()
        .addQueryParameter("select", seriesSelect)
        .addQueryParameter("type", "eq.comic")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiUrlBuilder()
            .addQueryParameter("order", "total_views.desc")
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()

        val mangas = apiGet(url)
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = apiUrlBuilder()
            .addQueryParameter("order", "updated_at.desc")
            .addQueryParameter("offset", ((page - 1) * pageSize).toString())
            .addQueryParameter("limit", pageSize.toString())
            .build()

        val mangas = apiGet(url)
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = apiUrlBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("or", "(title.ilike.*$query*,author.ilike.*$query*)")
            }

            val tagFilter = filters.firstInstanceOrNull<TagFilter>()
            if (tagFilter != null) {
                val included = tagFilter.included()
                val excluded = tagFilter.excluded()
                if (included.isNotEmpty()) {
                    addQueryParameter("tags", "cs.{${included.joinToString(",")}}")
                }
                excluded.forEach {
                    addQueryParameter("tags", "not.cs.{$it}")
                }
            }

            val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
            if (statusFilter != null && statusFilter.state != 0) {
                addQueryParameter("status", "eq.${statusFilter.selected()}")
            }

            addQueryParameter("order", "updated_at.desc")
            addQueryParameter("offset", ((page - 1) * pageSize).toString())
            addQueryParameter("limit", pageSize.toString())
        }.build()

        val mangas = apiGet(url)
            .parseAs<List<SeriesDto>>()
            .map { it.toSManga() }

        return MangasPage(mangas, mangas.isNotEmpty())
    }

    // =============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/truyen/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]?.stringOrNull ?: chapter.url.substringBefore("/")
        val number = chapter.memo["chapter_number"]?.stringOrNull
            ?: chapter.url.substringAfter("/", "")
        return "$baseUrl/truyen/$slug/$number"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() != "truyen") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        val detailUrl = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "*")
            .addQueryParameter("slug", "eq.$slug")
            .build()

        return apiGet(detailUrl)
            .parseAs<List<SeriesDto>>()
            .firstOrNull()
            ?.toSMangaDetails()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "*,chapters(id,chapter_number,title,created_at,is_locked)")
            .addQueryParameter("slug", "eq.${manga.url}")
            .build()

        val series = apiGet(url)
            .parseAs<List<SeriesDto>>()
            .first()

        return SMangaUpdate(
            manga = series.toSMangaDetails(),
            chapters = series.toChapterList(),
        )
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/chapter_images".toHttpUrl().newBuilder()
            .addQueryParameter("select", "image_url")
            .addQueryParameter("chapter_id", "eq.${chapter.url}")
            .addQueryParameter("order", "page_order.asc")
            .build()

        return apiGet(url)
            .parseAs<List<PageDto>>()
            .mapIndexed { index, page -> Page(index, imageUrl = page.imageUrl) }
    }

    // =============================== Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$apiUrl/series?select=tags&type=eq.comic".toHttpUrl()
        val tags = apiGet(url)
            .parseAs<List<TagListDto>>()
            .flatMap { it.tags }
            .distinct()
            .sorted()
        return tags.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<String>>()
        return getFilters(tags)
    }

    private val seriesSelect = "slug,title,cover_url,author,status,tags"
    private val pageSize = 24
}
