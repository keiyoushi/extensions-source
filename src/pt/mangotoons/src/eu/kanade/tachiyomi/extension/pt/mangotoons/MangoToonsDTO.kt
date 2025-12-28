package eu.kanade.tachiyomi.extension.pt.mangotoons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class MangoResponse<T>(
    val sucesso: Boolean? = false,
    val dados: T? = null,
    val obras: T? = null,
    val obra: T? = null,
    val data: T? = null,
    val capitulos: T? = null,
    val pagination: PaginationDto? = null,
) {
    val items: T
        get() = dados ?: obras ?: obra ?: data ?: capitulos ?: throw Exception("Data field not found")
}

@Serializable
class PaginationDto(
    val hasNextPage: Boolean = false,
)

@Serializable
class MangoMangaDto(
    @JsonNames("id", "slug", "unique_id")
    val id: Int? = null,
    @SerialName("title")
    @JsonNames("nome")
    val titulo: String,
    @SerialName("coverImage") val capa: String? = null,
    val imagem: String? = null,
    val views: Int? = null,
    val descricao: String? = null,
    val formato_nome: String? = null,
    val status_nome: String? = null,
    val tags: List<MangoTagDto>? = null,
    val capitulos: List<MangoChapterDto>? = null,
)

@Serializable
class MangoTagDto(
    val nome: String,
)

@Serializable
class MangoChapterDto(
    val id: Int,
    val obra_id: Int,
    val numero: Float,
    val nome: String? = null,
    @SerialName("criado_em") val data: String? = null,
)

@Serializable
class MangoPageResponse(
    val sucesso: Boolean? = false,
    val capitulo: MangoPageChapterDto? = null,
)

@Serializable
class MangoPageChapterDto(
    val paginas: List<MangoPageDto> = emptyList(),
)

@Serializable
class MangoPageDto(
    @SerialName("cdn_id") val url: String,
)

@Serializable
class MangoLatestChapterDto(
    val obra: MangoMangaDto? = null,
)
