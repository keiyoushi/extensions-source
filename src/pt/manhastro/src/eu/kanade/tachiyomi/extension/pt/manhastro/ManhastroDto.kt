package eu.kanade.tachiyomi.extension.pt.manhastro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T,
)

@Serializable
data class MangaDto(
    @SerialName("manga_id") val mangaId: Int,
    val titulo: String = "",
    @SerialName("titulo_brasil") val tituloBrasil: String? = null,
    private val descricao: String? = null,
    @SerialName("descricao_brasil") private val descricaoBrasil: String? = null,
    private val imagem: String? = null,
    private val capa: String? = null,
    val generos: String? = null,
    @SerialName("views_mes") private val viewsMes: String? = null,
    @SerialName("qnt_capitulo") val qntCapitulo: Int? = null,
) {
    val displayTitle: String get() = tituloBrasil?.takeIf { it.isNotBlank() } ?: titulo
    val displayDescription: String? get() = descricaoBrasil?.takeIf { it.isNotBlank() } ?: descricao
    val thumbnailUrl: String? get() = (imagem?.takeIf { it.isNotBlank() } ?: capa)?.let {
        if (it.startsWith("http")) it else "https://$it"
    }
    val popularity: Int get() = viewsMes?.toIntOrNull() ?: 0
}

@Serializable
data class RankingItemDto(
    @SerialName("manga_id") val mangaId: Int,
)

@Serializable
data class LatestItemDto(
    @SerialName("manga_id") val mangaId: Int,
)

@Serializable
data class ChapterDto(
    @SerialName("capitulo_id") val capituloId: Int,
    @SerialName("capitulo_nome") val capituloNome: String,
    @SerialName("capitulo_data") val capituloData: String,
)

@Serializable
data class PagesResponse(
    val success: Boolean,
    val data: PageData,
)

@Serializable
data class PageData(
    val chapter: ChapterData? = null,
)

@Serializable
data class ChapterData(
    val baseUrl: String,
    val hash: String,
    val data: List<String>,
)
