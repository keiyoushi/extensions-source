package eu.kanade.tachiyomi.extension.pt.manhastro

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ResponseWrapper<T>(
    val data: T,
)

@Serializable
class PopularMangaDto(
    @SerialName("manga_id")
    val id: Int,
)

@Serializable
class MangaDto(
    @SerialName("manga_id")
    val id: Int,
    @SerialName("titulo")
    val title: String,
    @SerialName("titulo_brasil")
    val titleLocalized: String?,
    @SerialName("descricao")
    val description: String,
    @SerialName("imagem")
    val thumbnailUrl: String?,
    @SerialName("generos")
    val genres: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        description = this@MangaDto.description
        thumbnail_url = "https://$thumbnailUrl"
        url = "/manga/${this@MangaDto.id}"
        genre = this@MangaDto.genres
    }
}
