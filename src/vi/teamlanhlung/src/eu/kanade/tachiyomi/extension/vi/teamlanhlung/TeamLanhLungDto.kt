package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponseDto(
    val data: List<SearchEntryDto>,
    val success: Boolean,
)

@Serializable
data class SearchEntryDto(
    val cstatus: String,
    val img: String,
    val isocm: Int,
    val link: String,
    val star: Float,
    val title: String,
    val vote: String,
)

@Serializable
data class CipherDto(
    val ciphertext: String,
    val iv: String,
    val salt: String,
)
