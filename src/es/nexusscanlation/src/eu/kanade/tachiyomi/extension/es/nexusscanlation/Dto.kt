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
    @SerialName("published_at")
    val publishedAt: String? = null,
)

@Serializable
class ChapterPagesPayloadDto(
    val data: ChapterPagesDto? = null,
)

@Serializable
class ChapterPagesDto(
    val paginas: List<PageEntryDto>? = null,
)

@Serializable
class PageEntryDto(
    val orden: Int = 0,
    val url: String = "",
)
