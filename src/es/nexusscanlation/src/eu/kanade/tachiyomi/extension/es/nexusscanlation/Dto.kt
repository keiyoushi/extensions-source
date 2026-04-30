package eu.kanade.tachiyomi.extension.es.nexusscanlation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogResponseDto(
    val data: List<CatalogEntryDto>? = null,
    val meta: CatalogMetaDto? = null,
)

@Serializable
class CatalogMetaDto(
    @SerialName("has_next")
    val hasNext: Boolean = false,
)

@Serializable
class CatalogEntryDto(
    val id: String,
    val slug: String,
    val titulo: String,
    @SerialName("portada_url")
    val portadaUrl: String? = null,
)

@Serializable
class SeriesPayloadDto(
    val serie: SeriesDto,
    val capitulos: List<ChapterEntryDto>? = null,
)

@Serializable
class SeriesDto(
    val id: String,
    val slug: String,
    val titulo: String,
    @SerialName("portada_url")
    val portadaUrl: String? = null,
    val descripcion: String? = null,
    val estado: String = "",
    val generos: List<NameDto>? = null,
    val autores: List<NameDto>? = null,
)

@Serializable
class NameDto(
    val nombre: String = "",
    val rol: String? = null,
)

@Serializable
class ChapterEntryDto(
    val slug: String,
    val numero: Float,
    val titulo: String? = null,
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("es_premium")
    val esPremium: Boolean = false,
)

// API may wrap chapter pages inside "data"
@Serializable
class ChapterPagesWrapperDto(
    val data: ChapterPagesDto? = null,
)

// The chapter pages data (used both as root and inside "data" wrapper)
@Serializable
class ChapterPagesDto(
    val paginas: List<PageEntryDto>? = null,
    @SerialName("es_premium")
    val esPremium: Boolean = false,
    val locked: Boolean = false,
)

@Serializable
class PageEntryDto(
    val orden: Int = 0,
    val url: String = "",
)
