package eu.kanade.tachiyomi.extension.es.nexusscanlation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Nexusscanlation : HttpSource() {

    override val name = "NexusScanlation"
    override val baseUrl = "https://nexusscanlation.com"
    override val lang = "es"
    override val supportsLatest = true

    private val apiBaseUrl = "https://api.nexusscanlation.com/api/v1"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun headersBuilder() = super.headersBuilder()

    private fun apiHeaders() = headersBuilder()
        .add("Accept", "application/json")
        .add("Referer", baseUrl)
        .build()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val pieces = chapter.url.split('/', limit = 2)
        if (pieces.size != 2) return baseUrl

        val seriesSlug = pieces[0]
        val chapterSlug = pieces[1]
        return "$baseUrl/series/$seriesSlug/chapter/$chapterSlug"
    }

    override fun popularMangaRequest(page: Int): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("catalog")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, apiHeaders())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val root = response.parseAs<CatalogResponseDto>()
        return MangasPage(root.data.mapNotNull(::catalogToManga), root.meta?.hasNext ?: false)
    }

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlBuilder = apiBaseUrl.toHttpUrl().newBuilder()

        if (query.isBlank()) {
            urlBuilder.addPathSegment("catalog")
        } else {
            urlBuilder
                .addPathSegment("catalog")
                .addPathSegment("search")
                .addQueryParameter("q", query)
        }

        urlBuilder.addQueryParameter("page", page.toString())
        return GET(urlBuilder.build(), apiHeaders())
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .build()
        return GET(url, apiHeaders())
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val root = response.parseAs<SeriesPayloadDto>()
        return seriesToManga(root.serie)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(manga.url)
            .build()
        return GET(url, apiHeaders())
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<SeriesPayloadDto>()
        val seriesSlug = payload.serie.slug

        return payload.capitulos
            .asSequence()
            .mapNotNull { chapterToModel(seriesSlug, it) }
            .sortedWith(compareByDescending<SChapter> { chapterSortKey(it) }.thenByDescending { it.name.orEmpty() })
            .toList()
    }

    private fun chapterSortKey(chapter: SChapter): Float {
        val number = chapter.chapter_number
        return if (number < 0f) Float.MIN_VALUE else number
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val pieces = chapter.url.split('/', limit = 2)
        require(pieces.size == 2) { "Invalid chapter url" }

        val url = apiBaseUrl.toHttpUrl().newBuilder()
            .addPathSegment("series")
            .addPathSegment(pieces[0])
            .addPathSegment("capitulos")
            .addPathSegment(pieces[1])
            .build()

        return GET(url, apiHeaders())
    }

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.parseAs<ChapterPagesPayloadDto>()
        return payload.data.paginas
            .asSequence()
            .filter { it.url.isNotBlank() }
            .sortedBy { it.orden }
            .mapIndexed { index, page -> Page(index, "", page.url) }
            .toList()
    }

    private fun catalogToManga(item: CatalogEntryDto): SManga? {
        if (item.slug.isBlank() || item.titulo.isBlank()) return null
        return SManga.create().apply {
            url = item.slug
            title = item.titulo
            thumbnail_url = item.portadaUrl
        }
    }

    private fun chapterToModel(seriesSlug: String, chapter: ChapterEntryDto): SChapter? {
        if (chapter.slug.isBlank()) return null

        val chapterLabel = when {
            chapter.numero == null -> chapter.slug
            chapter.numero % 1f == 0f -> chapter.numero.toInt().toString()
            else -> chapter.numero.toString()
        }

        return SChapter.create().apply {
            url = "$seriesSlug/${chapter.slug}"
            name = "Capitulo $chapterLabel"
            chapter_number = chapter.numero ?: -1f
            date_upload = parseIsoDate(chapter.publishedAt)
        }
    }

    private fun seriesToManga(series: SeriesDto): SManga = SManga.create().apply {
        title = series.titulo
        thumbnail_url = series.portadaUrl
        description = series.descripcion
        genre = series.generos.map { it.nombre }.filter { it.isNotBlank() }.joinToString().ifBlank { null }

        status = when (series.estado.lowercase(Locale.ROOT)) {
            "en_emision" -> SManga.ONGOING
            "finalizado" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val authorNames = series.autores.map { it.nombre }.filter { it.isNotBlank() }
        if (authorNames.isNotEmpty()) {
            val joined = authorNames.joinToString()
            author = joined
            artist = joined
        }
    }

    private fun parseIsoDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(value)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
