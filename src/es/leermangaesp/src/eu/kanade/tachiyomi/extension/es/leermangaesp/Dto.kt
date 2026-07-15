package eu.kanade.tachiyomi.extension.es.leermangaesp

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl

@Serializable
class MangaListDto(
    val resultados: List<MangaDto>,
    val page: Int,
    val total_pages: Int,
)

@Serializable
class MangaDto(
    val slug: String,
    val titulo: String,
    val portada: String? = null,
)

@Serializable
class HomeGridMangaDto(
    val slug: String,
    val titulo: String,
    val portada: String? = null,
    val fecha_publicacion: String? = null,
)

fun MangaDto.toSManga(imageBaseUrl: HttpUrl): SManga? {
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

fun HomeGridMangaDto.toSManga(imageBaseUrl: HttpUrl): SManga? {
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
