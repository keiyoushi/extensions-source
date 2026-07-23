package eu.kanade.tachiyomi.extension.vi.goctruyentranh

import kotlinx.serialization.Serializable

@Serializable
class SearchDTO(
    val comics: Comics,
)

@Serializable
class Data(
    val name: String,
    val slug: String,
    val thumbnail: String?,
)

@Serializable
class Comics(
    val current_page: Int,
    val data: List<Data> = emptyList(),
    val last_page: Int,
)
