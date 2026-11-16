package eu.kanade.tachiyomi.multisrc.koi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class SearchResponse(
    val data: List<SearchResultDto> = emptyList(),
)

@Serializable
class SearchResultDto(
    val title: String,
    val link: String,
    private val img: JsonPrimitive? = null,
) {
    // Entries without a cover send `false` instead of a URL
    val cover: String? get() = img?.takeIf { it.isString }?.content
}

@Serializable
class EncryptedContent(
    val ciphertext: String,
    val iv: String,
    val salt: String,
)
