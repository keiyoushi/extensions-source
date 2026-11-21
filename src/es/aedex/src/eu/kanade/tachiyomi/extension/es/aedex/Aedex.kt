package eu.kanade.tachiyomi.extension.es.aedex

import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.ChapterDto
import eu.kanade.tachiyomi.multisrc.catharsis.GenreDto
import eu.kanade.tachiyomi.multisrc.catharsis.MangaChaptersDto
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import eu.kanade.tachiyomi.multisrc.catharsis.Step
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class Aedex : Catharsis() {

    override val listingPath = "comics"

    override val mangaPath = "comic"

    override val chapterSegment = ""

    override val sorts = listOf(
        "Última actualización" to null,
        "Más populares" to "total_views",
        "Recién agregados" to "created_at",
        "Título" to "title",
    )

    override val popularSort = 1

    override val latestSort = 0

    override val statuses = listOf("En Emisión", "En Pausa", "Finalizado", "Cancelado")

    override val ascendingOrder = "ascendente"

    override val readerHosts = setOf("blayvia.com")

    /** The page's server-rendered flight stream. */
    private suspend fun stream(url: String): String = siteView.drive(url, "(${siteView.asset("payload.js")})()") { result ->
        Step.Done(result.parseAs<Payload>().stream)
    }

    override suspend fun readListing(url: HttpUrl): MangasPage {
        val listing = stream(url.toString())
            .extractNextJsRsc<Listing> { it is JsonObject && it["allComics"] is JsonArray && it["initialPagination"] is JsonObject }
            ?: throw IOException("La lista de series no estaba en la página")
        return MangasPage(
            listing.allComics.map { it.toSManga(it.slug, baseUrl) },
            listing.initialPagination.page < listing.initialPagination.totalPages,
        )
    }

    override suspend fun readSeries(manga: SManga, known: List<SChapter>, withChapters: Boolean): SMangaUpdate {
        val series = stream(getMangaUrl(manga))
            .extractNextJsRsc<JsonElement> { it is JsonObject && it["capitulos"] is JsonArray && "slug" in it && "nombre" in it }
            ?: throw IOException("La serie no estaba en la página del sitio")
        val dto = series.parseAs<MangaDto>()
        return SMangaUpdate(
            dto.toSManga(dto.slug, baseUrl),
            series.parseAs<MangaChaptersDto>().capitulos
                .filter { it.estado == null || it.estado == "publicado" }
                .sortedWith(compareByDescending<ChapterDto> { it.temporada }.thenByDescending { it.numero })
                .map { it.toSChapter(manga.url, dto.scanlator, Instant.tryParse(it.dateCreated)) },
        )
    }

    override val readerPages = "canvas[data-page]"

    override fun readerRefusal(text: String): String? = when {
        "Los visitantes solo pueden leer" in text -> "Inicia sesión en el sitio desde WebView para leer más allá del capítulo 3"
        "No se puede leer desde aquí" in text -> "La verificación de Google del sitio rechazó el lector; inténtalo más tarde o lee en WebView"
        else -> null
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = stream("$baseUrl/$listingPath").extractNextJsRsc<Genres> { it is JsonObject && it["initialGenres"] is JsonArray }
            ?: throw IOException("Los géneros no estaban en la página")
        return genres.initialGenres.toJsonElement()
    }

    override fun genres(data: JsonElement?) = data?.parseAs<List<GenreDto>>().orEmpty().map { it.nombre to it.id }
}
