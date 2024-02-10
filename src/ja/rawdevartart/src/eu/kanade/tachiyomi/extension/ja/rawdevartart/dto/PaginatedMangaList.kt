package eu.kanade.tachiyomi.extension.ja.rawdevartart.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ButtonData(
    val prev: Int,
    val next: Int,
)

@Serializable
data class PaginationData(
    val button: ButtonData? = null,
)

@Serializable
data class PaginatedMangaList(
    @SerialName("manga_list") val mangaList: List<MangaDto>,
    val pagi: PaginationData,
)
