package eu.kanade.tachiyomi.extension.es.leermangaesp

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class MangaListDto(
    val resultados: List<MangaDto>,
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaDto(
    private val slug: String,
    private val titulo: String,
    private val portada: String? = null,
) {
    fun toSManga(imageBaseUrl: HttpUrl): SManga? = buildSManga(slug, titulo, portada, imageBaseUrl)
}

@Serializable
class HomeGridMangaDto(
    private val slug: String,
    private val titulo: String,
    private val portada: String? = null,
    @SerialName("fecha_publicacion") val fechaPublicacion: String? = null,
) {
    fun toSManga(imageBaseUrl: HttpUrl): SManga? = buildSManga(slug, titulo, portada, imageBaseUrl)
}

private fun buildSManga(slug: String, titulo: String, portada: String?, imageBaseUrl: HttpUrl): SManga? {
    if (titulo.isBlank()) return null

    return SManga.create().apply {
        url = slug
        title = titulo
        thumbnail_url = portada
            ?.removePrefix("/")
            ?.takeIf(String::isNotBlank)
            ?.let { relPath ->
                imageBaseUrl.newBuilder()
                    .addPathSegments(relPath)
                    .build()
                    .toString()
            }
    }
}
