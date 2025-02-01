package eu.kanade.tachiyomi.extension.vi.goctruyentranh

import kotlinx.serialization.Serializable

@Serializable
class SearchDTO(
    val comics: Comics,
)

@Serializable
class Data(
    var name: String,
    val slug: String,
    val thumbnail: String?,
)

@Serializable
class Comics(
    val current_page: Int,
    val data: ArrayList<Data> = arrayListOf(),
    val last_page: Int,
)
