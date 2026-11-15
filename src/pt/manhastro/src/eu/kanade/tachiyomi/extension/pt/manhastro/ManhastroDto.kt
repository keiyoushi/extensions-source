package eu.kanade.tachiyomi.extension.pt.manhastro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiResponse<T>(
    val data: T,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class MangaDto(
    @SerialName("manga_id") val mangaId: Int,
    val titulo: String = "",
    @SerialName("titulo_brasil") private val tituloBrasil: String? = null,
    private val descricao: String? = null,
    @SerialName("descricao_brasil") private val descricaoBrasil: String? = null,
    private val imagem: String? = null,
    val generos: List<String> = emptyList(),
    val status: String? = null,
) {
    val displayTitle: String get() = tituloBrasil?.takeIf { it.isNotBlank() } ?: titulo
    val displayDescription: String? get() = descricaoBrasil?.takeIf { it.isNotBlank() } ?: descricao
    val thumbnailUrl: String? get() = imagem?.let {
        if (it.startsWith("http")) it else "https://$it"
    }
}

@Serializable
class ChapterDto(
    @SerialName("capitulo_id") val capituloId: Int,
    @SerialName("capitulo_nome") val capituloNome: String,
    @SerialName("capitulo_data") val capituloData: String,
)

@Serializable
class PagesResponse(
    val data: PageData,
)

@Serializable
class PageData(
    val chapter: ChapterData? = null,
)

@Serializable
class ChapterData(
    val baseUrl: String,
    val hash: String,
    val data: List<String>,
)
