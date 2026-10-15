package eu.kanade.tachiyomi.extension.all.kyokotsu

import android.util.Base64
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

@Source
abstract class Kyokotsu : KeiSource() {

    private val preferences by getPreferencesLazy()

    private var cachedGenreMap: Map<String, String>? = null

    private val loc = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "ru"),
        classLoader = this::class.java.classLoader!!,
    )

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("popularity", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest("updated", page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("popularity", page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("catalog")

            filters?.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.selected?.forEach { addQueryParameter("genre", it) }
                    is StatusFilter -> filter.selected?.forEach { addQueryParameter("status", it) }
                    is AgeLimit -> filter.selected?.forEach { addQueryParameter("age", it) }
                    is YearReleaseFilter -> {
                        filter.minValue?.let { addQueryParameter("year_min", it) }
                        filter.maxValue?.let { addQueryParameter("year_max", it) }
                    }
                    is TypeFilter -> filter.selected?.let { addQueryParameter("type", it) }
                    is OrderBy -> filter.selected?.let { addQueryParameter("sort", it) }
                    else -> {}
                }
            }

            if (filters == null) addQueryParameter("sort", sortBy)

            if (query?.isNotBlank() == true) addQueryParameter("q", query)

            addQueryParameter("limit", PAGINATION.toString())
            addQueryParameter("offset", ((page - 1) * PAGINATION).toString())
            addQueryParameter("lang", lang)
        }.build()

        return client.get(url).use { response ->
            val data = response.parseAs<ApiResponse<SearchDto>>().data
            val mangas = data.items.map { it.toSManga(lang, baseUrl) }
            val hasNextPage = page * PAGINATION < data.total
            MangasPage(mangas, hasNextPage)
        }
    }

    // ============================== Deeplink ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val type = if (url.pathSegments[0] == "en") url.pathSegments[1] else url.pathSegments[0]
        val slug = if (url.pathSegments[0] == "en") url.pathSegments[2] else url.pathSegments[1]

        if (!STATUS.contains(type)) return null
        if (slug.isEmpty()) return null

        val tmpManga = SManga.create().apply {
            this.url = slug
        }
        return getMangaUpdate(tmpManga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // ============================== Manga ===============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaNew = if (fetchDetails || (fetchChapters && manga.memo["source"]?.stringOrNull == null)) {
            val url = "$baseUrl/title?slug=${manga.url}"
            val engGenres = if (lang != "ru") getGenreMap() else emptyMap()
            client.get(url).parseAs<ApiResponse<FullMangaDto>>().data.toSManga(lang, baseUrl, engGenres, loc)
        } else {
            manga
        }

        val chaptersNew = if (fetchChapters) {
            val ending = if (lang != "ru") "&lang=$lang" else ""
            val url = "$baseUrl/chapters/live?fresh=1&slug=${manga.url}$ending"
            val response = client.get(url).parseAs<ApiResponse<List<ChapterDto>>>()
            response.data.map { it.toSChapter(lang, mangaNew.memo) }.asReversed()
        } else {
            chapters
        }

        return SMangaUpdate(mangaNew, chaptersNew)
    }

    override fun getMangaUrl(manga: SManga): String {
        val loc = if (lang != "ru") "/$lang" else ""
        if (manga.memo["type"]?.stringOrNull.isNullOrEmpty()) return "$baseUrl$loc/catalog?q=${manga.title}"
        return "$baseUrl$loc/${manga.memo["type"]!!.string}/${manga.url}"
    }

    // ============================== Pages ===============================
    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${if (lang != "ru") "/$lang" else ""}/read/${chapter.memo["slug"]!!.string}/${chapter.memo["number"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val source = chapter.memo["source"]!!.string
        val slug = chapter.memo["slug"]!!.string
        val id = chapter.memo["id"]!!.string
        val mangalib = chapter.memo["mangalib"]?.boolean == true
        val inkstory = chapter.memo["inkstory"]?.boolean == true
        val weebcentral = chapter.memo["weebcentral"]?.boolean == true
        val mangadex = chapter.memo["mangadex"]?.boolean == true

        val url: String = when {
            mangalib -> {
                val branch = chapter.memo["branch"]?.stringOrNull?.let { "&branch_id=$it" } ?: ""
                "$baseUrl/mangalib/pages?slug=$slug&volume=${chapter.memo["vol"]!!.string}&number=${chapter.memo["num"]!!.string}$branch"
            }
            inkstory || source == "inkstory" -> "$baseUrl/inkstory/pages?chapter_id=$id&slug=$slug&chapter_num=${chapter.memo["number"]!!.string}&_=${Instant.now().toEpochMilli()}"
            weebcentral || source == "weebcentral" -> "$baseUrl/weebcentral/pages?chapter_id=$id"
            mangadex || source == "mangadex" -> "$baseUrl/mangadex/pages?chapter_id=$id"
            source == "mangalib" -> {
                val branch = chapter.memo["branch"]?.stringOrNull?.let { "&branch_id=$it" } ?: ""
                "$baseUrl/mangalib/pages?slug=$slug&volume=${chapter.memo["vol"]!!.string}&number=${chapter.memo["num"]!!.string}$branch"
            }
            else -> "$baseUrl/proxy?e=" + Base64.encodeToString("https://api.remanga.org/api/titles/chapters/$id/".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        }

        val response = client.get(url, ensureSuccess = false).use { resp ->
            val body = resp.body.string()
            val apiError = runCatching { body.parseAs<ApiError>() }.getOrNull()
            if (apiError?.error?.isNotBlank() == true) throw Exception(apiError.error)
            if (!resp.isSuccessful) throw HttpException(resp.code)
            body
        }

        val finalData = if (url.contains("proxy?e=")) {
            val data = response.parseAs<RemangaDto>()

            val body = ImageRequest(
                urls = data.content.pages?.flatten()?.mapNotNull { it.link?.replace("img.reimg.org", "img.reimg2.org") },
            ).toJsonRequestBody()

            client.post("$baseUrl/proxy/sign", body).parseAs<ApiResponse<List<String>>>()
        } else {
            response.parseAs<ApiResponse<List<String>>>()
        }

        return finalData.data.mapIndexed { index, string ->
            Page(index, imageUrl = "$baseUrl$string")
        }
    }

    // =========================== Related Manga ============================
    override val supportsRelatedMangas: Boolean = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val url = "$baseUrl/similar?slug=${manga.url}&lang=$lang"
        return client.get(url).parseAs<ApiResponse<List<SearchMangaDto>>>().data.map { it.toSManga(lang, baseUrl) }
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val response = client.get("$baseUrl/genres?limit=500").parseAs<ApiResponse<List<Genres>>>()

        val data = if (lang == "ru") {
            response.data.map { it.nameRu to it.nameRu }.sortedBy { it.first }
        } else {
            response.data.map { it.nameEn to it.nameEn }.sortedBy { it.first }
        }

        response.data.associate { it.nameRu to it.nameEn }.takeIf { it.isNotEmpty() }?.let {
            cachedGenreMap = it
            preferences.edit().putString(PREF_ENG_GENRES, it.toJsonString()).apply()
        }

        return FiltersDto(
            genres = data,
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters.add(OrderBy(loc["filter_order"], sortList))

        data?.parseAs<FiltersDto>()?.let {
            if (it.genres?.isNotEmpty() == true) filters.add(GenreFilter(loc["filter_genres"], it.genres))
        }

        filters.addAll(
            listOf(
                TypeFilter(loc["filter_type"], typeList),
                StatusFilter(loc["filter_status"], statusList),
                AgeLimit(loc["filter_age"]),
                // ChaptersFilter(loc["filter_chapters"], loc["filter_from"], loc["filter_to"], 0, 99999),
                // RatingFilter(loc["filter_rating"], loc["filter_from"], loc["filter_to"], 1, 10),
                YearReleaseFilter(loc["filter_release_year"], loc["filter_from"], loc["filter_to"], 1970, 2030),
            ),
        )

        return FilterList(filters)
    }

    // ============================== Localization ===============================
    private val sortList = listOf(
        loc["order_popular"] to "popularity",
        loc["order_rating"] to "rating",
        loc["order_new"] to "date",
        loc["order_updated"] to "updated",
    )

    private val statusList = listOf(
        loc["status_ongoing"] to "ongoing",
        loc["status_completed"] to "completed",
        loc["status_hiatus"] to "hiatus",
        loc["status_cancelled"] to "cancelled",
    )

    private val typeList = listOf(
        loc["type_all"] to "",
        loc["type_manga"] to "Манга",
        loc["type_manhwa"] to "Манхва",
        loc["type_manhua"] to "Маньхуа",
        loc["type_comics"] to "Комикс",
    )

    // ============================== Utilities ===============================
    private fun getGenreMap(): Map<String, String> {
        cachedGenreMap?.let { return it }
        val json = preferences.getString(PREF_ENG_GENRES, null) ?: return emptyMap()
        return runCatching { json.parseAs<Map<String, String>>() }
            .getOrDefault(emptyMap())
            .also { cachedGenreMap = it }
    }

    companion object {
        private const val PAGINATION = 20
        private const val PREF_ENG_GENRES = "cached_en_genres"
        private val STATUS = setOf("manga", "manhwa", "manhua", "comics", "oel-manga", "rumanga", "runet-comics")
    }
}
