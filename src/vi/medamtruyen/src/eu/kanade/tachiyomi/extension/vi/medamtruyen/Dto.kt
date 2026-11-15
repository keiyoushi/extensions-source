package eu.kanade.tachiyomi.extension.vi.medamtruyen

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val success: Boolean = false,
    val data: List<SearchResultDto> = emptyList(),
)

@Serializable
class SearchResultDto(
    val title: String? = null,
    val link: String? = null,
    val img: String? = null,
    val cstatus: String? = null,
    val view: String? = null,
)

@Serializable
class EncryptedContentDto(
    val ciphertext: String? = null,
    val iv: String? = null,
    val salt: String? = null,
)
