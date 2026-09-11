package eu.kanade.tachiyomi.extension.es.nexusscanlation

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.Locale
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class Nexusscanlation : KeiSource() {
    private val apiBaseUrlHost by lazy { apiBaseUrl.toHttpUrl().host }

    private val apiBaseUrl = "https://api.nexusscanlation.com/api/v1"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(ImageInterceptor())
        .rateLimit(1, 3.seconds) { it.host == apiBaseUrlHost } // API: max 1 request per 3 seconds

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept-Language", "es-419,es;q=0.9,es-ES;q=0.8")

    private val apiHeaders by lazy {
        headers.newBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("sec-fetch-dest", "empty")
            .add("sec-fetch-mode", "cors")
            .add("sec-fetch-site", "same-site")
            .build()
    }

    // ======================= Manga URLs ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (seriesSlug, chapterSlug) = chapter.url.split('/', limit = 2)
        return "$baseUrl/series/$seriesSlug/chapter/$chapterSlug"
    }

    // ======================= Popular ======================================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("orden", "popular")
            .build()

        val root = client.get(url, apiHeaders).parseAs<CatalogResponseDto>()
        return MangasPage(root.data.orEmpty().mapNotNull(::catalogToManga), root.meta?.hasNext ?: false)
    }

    // ======================= Latest =======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) {
            return MangasPage(emptyList(), false)
        }

        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("public")
            .addPathSegment("landing")
            .addQueryParameter("page", page.toString())
            .build()

        val root = client.get(url, apiHeaders).parseAs<LandingResponseDto>()
        val mangaList = root.latestUpdates.orEmpty()
            .distinctBy { it.serieSlug }
            .mapNotNull(::landingToManga)
        return MangasPage(mangaList, false)
    }

    // ======================= Search =======================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = apiBaseUrl.toHttpUrl().newBuilder()
                .addPathSegment("catalog")
                .addPathSegment("search")
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            val root = client.get(url, apiHeaders).parseAs<CatalogResponseDto>()
            return MangasPage(root.data.orEmpty().mapNotNull(::catalogToManga), root.meta?.hasNext ?: false)
        }

        val urlBuilder = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    urlBuilder.addQueryParameter("orden", filter.selectedValue())
                }
                is StatusFilter -> {
                    filter.selectedValue().takeIf { it.isNotBlank() }?.let {
                        urlBuilder.addQueryParameter("estado", it)
                    }
                }
                is TypeFilter -> {
                    filter.selectedValue().takeIf { it.isNotBlank() }?.let {
                        urlBuilder.addQueryParameter("tipo", it)
                    }
                }
                is GenreFilter -> {
                    filter.selectedValue().takeIf { it.isNotBlank() }?.let {
                        urlBuilder.addQueryParameter("genero", it)
                    }
                }
                else -> {}
            }
        }

        val root = client.get(urlBuilder.build(), apiHeaders).parseAs<CatalogResponseDto>()
        return MangasPage(root.data.orEmpty().mapNotNull(::catalogToManga), root.meta?.hasNext ?: false)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiBaseUrl/catalog/genres?has_series=true", apiHeaders).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.runCatching {
            parseAs<GenresResponseDto>().data.orEmpty().map { it.nombre to it.slug }
        }?.getOrNull()?.takeIf { it.isNotEmpty() }?.let { listOf("Todos" to "") + it } ?: DEFAULT_GENRES

        return FilterList(
            SortFilter(),
            StatusFilter(),
            TypeFilter(),
            GenreFilter(genres),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val currentHost = baseUrl.toHttpUrl().host
        if (!url.host.equals(currentHost, ignoreCase = true)) return null

        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "series") return null
        val slug = segments[1].takeIf { it.isNotBlank() } ?: return null

        val apiUrl = "$apiBaseUrl/series/$slug".toHttpUrl()
        val payload = client.get(apiUrl, apiHeaders).parseAs<SeriesPayloadDto>()
        return seriesToManga(payload.serie)
    }

    // ======================= Details and Chapters =========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiBaseUrl/series/${manga.url}".toHttpUrl()
        val payload = client.get(url, apiHeaders).parseAs<SeriesPayloadDto>()

        val series = seriesToManga(payload.serie)
        val seriesSlug = payload.serie.slug
        val chapterList = payload.capitulos.orEmpty().map { chapterToModel(seriesSlug, it) }

        return SMangaUpdate(series, chapterList)
    }

    // ======================= Pages ========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (seriesSlug, chapterSlug) = chapter.url.split('/', limit = 2)

        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(seriesSlug)
            .addPathSegment("capitulos")
            .addPathSegment(chapterSlug)
            .build()

        val response = client.get(url, apiHeaders)
        val body = response.body.string()

        val chapterPagesDto = runCatching { body.parseAs<ChapterPagesWrapperDto>().data }
            .getOrNull()
            ?: runCatching { body.parseAs<ChapterPagesDto>() }.getOrNull()
            ?: throw IOException("Failed to decode server response.")

        if (chapterPagesDto.esPremium || chapterPagesDto.locked) {
            throw IOException("Premium chapter. Not available.")
        }

        return chapterPagesDto.paginas.orEmpty().mapIndexed { index, page ->
            val imageUrl = buildString {
                append(page.url)
                page.scrambledData?.let {
                    append("#scramble=")
                    append(it.columns)
                    append(',')
                    append(it.rows)
                    append(',')
                    append(it.seed)
                    append(',')
                    append(it.version)
                }
            }

            Page(index, imageUrl = imageUrl)
        }
    }

    // ======================= Helpers =======================================

    private fun catalogToManga(item: CatalogEntryDto): SManga? {
        if (item.slug.isBlank() || item.titulo.isBlank()) return null
        return SManga.create().apply {
            url = item.slug
            title = item.titulo
            thumbnail_url = resolveCoverUrl(item.portadaUrl, item.id)
        }
    }

    private fun landingToManga(item: LandingUpdateDto): SManga? {
        if (item.serieSlug.isBlank() || item.serieTitulo.isBlank()) return null
        return SManga.create().apply {
            url = item.serieSlug
            title = item.serieTitulo
            thumbnail_url = resolveCoverUrl(item.portadaUrl, item.serieId)
        }
    }

    private fun chapterToModel(seriesSlug: String, chapter: ChapterEntryDto): SChapter {
        val chapterNumber = chapter.numero.toString().removeSuffix(".0")

        val chapterName = buildString {
            if (chapter.esPremium) {
                append("🔒 ")
            }

            append("Capítulo $chapterNumber")

            chapter.titulo
                ?.takeIf { it.isNotBlank() }
                ?.let { title ->
                    append(" - $title")
                }
        }

        return SChapter.create().apply {
            url = "$seriesSlug/${chapter.slug}"
            name = chapterName
            chapter_number = chapter.numero
            date_upload = Instant.tryParse(chapter.publishedAt)
        }
    }

    private fun seriesToManga(series: SeriesDto): SManga = SManga.create().apply {
        url = series.slug
        title = series.titulo
        thumbnail_url = resolveCoverUrl(series.portadaUrl, series.id)
        description = series.descripcion
        genre = series.generos
            ?.mapNotNull { it.nombre.takeIf { name -> name.isNotBlank() } }
            ?.joinToString()

        status = when (series.estado.lowercase(Locale.ROOT)) {
            "en_emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val credits = series.autores.orEmpty().mapNotNull { credit ->
            credit.nombre.takeIf { it.isNotBlank() }?.trim()?.let { it to credit.rol?.lowercase(Locale.ROOT) }
        }

        author = credits
            .filter { (_, role) -> role != "artista" }
            .map { (name) -> name }
            .distinct()
            .joinToString()
            .ifBlank { null }

        artist = credits
            .filter { (_, role) -> role == "artista" }
            .map { (name) -> name }
            .distinct()
            .joinToString()
            .ifBlank { null }
        initialized = true
    }

    private fun resolveCoverUrl(rawUrl: String?, seriesId: String?): String? {
        // Use CDN url to prevent DDoS autobans from their WAF
        if (!seriesId.isNullOrBlank()) {
            return "https://cdn.nexusscanlation.com/series/$seriesId/portada.jpg"
        }
        return rawUrl.takeIf { !it.isNullOrBlank() }
    }
}
