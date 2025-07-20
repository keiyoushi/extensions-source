package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import kotlinx.serialization.Serializable

@Serializable
class SearchDTO(
    val result: List<ResultSearch>,
)

@Serializable
class ResultSearch(
    val name: String,
    val photo: String,
    val nameEn: String,
)
