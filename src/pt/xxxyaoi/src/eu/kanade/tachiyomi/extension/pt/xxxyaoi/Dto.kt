package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LibraryResponse(val data: LibraryData)

@Serializable
class LibraryData(
    val html: String,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class Genre(val name: String, val slug: String)
