package eu.kanade.tachiyomi.extension.es.shadowmanga

import android.util.Base64
import eu.kanade.tachiyomi.extension.es.shadowmanga.interceptor.ImageFallbackInterceptor
import eu.kanade.tachiyomi.source.model.Filter
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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ShadowManga : KeiSource() {
    private val isNsfw by lazy { name.contains("+18") }

    private val cdnHosts = listOf(
        "media.shademanga.com",
        "cdn.shademanga.com",
    )

    private val fallbackPrefix = "/api/media/"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageFallbackInterceptor(cdnHosts, "shademanga.com", fallbackPrefix))
        .rateLimit(2, 1.seconds, 500.milliseconds) { it.host == "shademanga.com" }

    private fun getAdultPageToken(page: Int, pageSize: Int = 24): String {
        val half = pageSize / 2
        val lo = (page - 1) * half
        val so = (page - 1) * half
        val json = """{"lo":$lo,"so":$so}"""
        val b64 = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "cx_$b64"
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = if (isNsfw) {
        val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
            .addQueryParameter("orden", "vistos")
            .addQueryParameter("pageSize", "48")
        if (page > 1) {
            url.addQueryParameter("p", getAdultPageToken(page, 48))
        }
        val result = client.get(url.build()).parseAs<AdultCatalogResponse>()
        val mangas = result.items
            .filter { !it.externo }
            .map { it.toSManga() }
        val hasNextPage = mangas.isNotEmpty() && (result.pageTokens?.next != null || result.page < result.totalPages)
        MangasPage(mangas, hasNextPage)
    } else {
        val result = client.get("$baseUrl/api/series-locales/popular").parseAs<List<SeriesWrapper>>()
        val series = result.flatMap { it.series }.distinctBy { it.id }.map { it.toSManga() }
        MangasPage(series, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = if (isNsfw) {
        val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
            .addQueryParameter("orden", "recientes")
            .addQueryParameter("pageSize", "48")
        if (page > 1) {
            url.addQueryParameter("p", getAdultPageToken(page, 48))
        }
        val result = client.get(url.build()).parseAs<AdultCatalogResponse>()
        val mangas = result.items
            .filter { !it.externo }
            .map { it.toSManga() }
        val hasNextPage = mangas.isNotEmpty() && (result.pageTokens?.next != null || result.page < result.totalPages)
        MangasPage(mangas, hasNextPage)
    } else {
        val result = client.get("$baseUrl/api/series-locales/capitulos/recientes?page=$page&pageSize=24").parseAs<RecentChaptersResponse>()
        val mangas = result.items.map { it.serie.toSManga() }.distinctBy { it.url }
        val hasNextPage = result.page < result.totalPages
        MangasPage(mangas, hasNextPage)
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val excludedGenres = genreFilter?.getExcluded().orEmpty()
        val includedGenres = genreFilter?.getIncluded().orEmpty()

        if (isNsfw) {
            val url = "$baseUrl/api/series-locales/adultos".toHttpUrl().newBuilder()
                .addQueryParameter("pageSize", "24")
            if (page > 1) {
                url.addQueryParameter("p", getAdultPageToken(page))
            }
            if (query.isNotBlank()) {
                url.addQueryParameter("q", query)
            }
            if (includedGenres.isNotEmpty()) {
                url.addQueryParameter("generos", includedGenres.joinToString(","))
            }
            val orderByFilter = filters.firstInstanceOrNull<OrderByFilter>()
            val orden = when (orderByFilter?.state) {
                1 -> "vistos"
                2 -> "az"
                else -> "recientes"
            }
            if (orden != "recientes") {
                url.addQueryParameter("orden", orden)
            }
            val result = client.get(url.build()).parseAs<AdultCatalogResponse>()
            val mangas = result.items
                .filter { series ->
                    excludedGenres.isEmpty() || series.getGenreList().none { genre ->
                        genre in excludedGenres
                    }
                }
                .map { it.toSManga() }
            val hasNextPage = result.items.isNotEmpty() && (result.pageTokens?.next != null || result.page < result.totalPages)
            return MangasPage(mangas, hasNextPage)
        }

        val url = "$baseUrl/api/series-locales/search-candidates".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("includeAdult", "false")
            .addQueryParameter("showSinPortada", "false")
            .addQueryParameter("take", MAX_RESULTS.toString())

        includedGenres.forEach {
            url.addQueryParameter("tags", it)
        }

        val mangas = client.get(url.build()).parseAs<List<Series>>()
            .filter { series ->
                excludedGenres.isEmpty() || series.getGenreList().none { genre ->
                    genre in excludedGenres
                }
            }.sortedBy { it.title }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.equals(baseUrl.toHttpUrl().host, ignoreCase = true)) return null
        val segments = url.pathSegments
        if (segments.size >= 3 && segments[0] == "serie" && segments[1] == "local") {
            val id = segments[2]
            val series = client.get("$baseUrl/api/series-locales/$id").parseAs<Series>()
            return series.toSMangaDetails()
        }
        if (segments.size >= 4 && segments[0] == "adultos" && segments[1] == "manga" && segments[2] == "o") {
            val smId = segments[3]
            val oneshot = client.get("$baseUrl/api/series-locales/ext/$smId").parseAs<OneshotDetails>()
            return oneshot.toSMangaDetails()
        }
        return null
    }

    // ======================= Manga URLs ===================================

    override fun getMangaUrl(manga: SManga): String = if (manga.url.startsWith("ext/")) {
        "$baseUrl/adultos/manga/o/${manga.url.removePrefix("ext/")}"
    } else {
        "$baseUrl/serie/local/${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String = if (chapter.url.startsWith("ext/")) {
        val smId = chapter.url.removePrefix("ext/").substringBefore("/")
        "$baseUrl/adultos/manga/o/$smId"
    } else {
        val chapterId = chapter.url.substringAfter("/")
        "$baseUrl/reader/local/$chapterId"
    }

    // ======================= Details and Chapters =========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (manga.url.startsWith("ext/")) {
            val smId = manga.url.removePrefix("ext/")
            val oneshot = client.get("$baseUrl/api/series-locales/ext/$smId").parseAs<OneshotDetails>()
            val chapter = SChapter.create().apply {
                name = "Capítulo 1"
                url = "ext/${oneshot.smId}/${oneshot.chapterId}"
                date_upload = 0L
            }
            return SMangaUpdate(oneshot.toSMangaDetails(), listOf(chapter))
        }

        val series = client.get("$baseUrl/api/series-locales/${manga.url}").parseAs<Series>()
        val chapterList = series.chapters
            .sortedByDescending { it.chapterNumber }
            .map { it.toSChapter(series.id) }
        return SMangaUpdate(series.toSMangaDetails(), chapterList)
    }

    // ======================= Pages ========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = if (chapter.url.startsWith("ext/")) {
            val smId = chapter.url.removePrefix("ext/").substringBefore("/")
            "$baseUrl/api/series-locales/ext/$smId/paginas"
        } else {
            val mangaId = chapter.url.substringBefore("/")
            val chapterId = chapter.url.substringAfter("/")
            "$baseUrl/api/series-locales/$mangaId/capitulos/$chapterId/paginas"
        }
        val result = client.get(url).parseAs<PagesWrapper>()
        return result.pages.mapIndexed { index, pageUrl ->
            Page(index, imageUrl = pageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = !isNsfw

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/series-locales/tags").parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<String>>().orEmpty()
        return if (isNsfw) {
            FilterList(
                OrderByFilter(),
                GenreFilter(adultGenres),
            )
        } else {
            if (tags.isEmpty()) {
                FilterList(Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros."))
            } else {
                FilterList(GenreFilter(tags.sorted()))
            }
        }
    }

    private val adultGenres = listOf(
        "Ahegao", "Anal", "Bañador", "Bondage", "Control mental", "Creampie",
        "DILF", "Doble penetración", "Embarazada", "Enfermera", "Femdom",
        "Footjob", "Futanari", "Gafas", "Grupal", "Gyaru", "Harén", "Incesto",
        "Infidelidad", "Juguetes", "Lactancia", "MILF", "Maid", "Medias",
        "Mind break", "Musculosa", "Netorare", "Netorase", "Non-con", "Oral",
        "Paizuri", "Pechos enormes", "Pechos grandes", "Piel morena", "Preñez",
        "Primera vez", "Profesora", "Tomboy", "Trasero grande", "Uniforme escolar",
        "Yaoi", "Yuri",
    )

    companion object {
        const val MAX_RESULTS = 120
    }
}
