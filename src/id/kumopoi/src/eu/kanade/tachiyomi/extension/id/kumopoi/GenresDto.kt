package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.multisrc.aesir.GenreDto
import kotlinx.serialization.Serializable

@Serializable
class GenresDto(
    val pageKey: String,
    val genres: List<GenreDto>,
)
