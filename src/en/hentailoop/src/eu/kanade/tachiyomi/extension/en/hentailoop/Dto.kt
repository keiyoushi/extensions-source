package eu.kanade.tachiyomi.extension.en.hentailoop

import kotlinx.serialization.Serializable

@Serializable
class SearchResponseDto(
    private val data: SearchDataDto? = null,
) {
    val posts get() = data?.posts ?: emptyList()
    val hasNextPage get() = data?.more ?: false
}

@Serializable
class SearchDataDto(
    val posts: List<String> = emptyList(),
    val more: Boolean = false,
)
