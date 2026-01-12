package eu.kanade.tachiyomi.extension.tr.sadscans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

val json: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Serializable
data class SadScansSearch(val result: SearchResult?)

@Serializable
data class SearchResult(val data: SearchData?)

@Serializable
data class SearchData(val json: List<SearchJson>?)

@Serializable
data class SearchJson(
    val name: String?,
    val thumb: String?,
    val href: String?,
    val author: String?,
    val status: String?,
)

@Serializable
data class TrpcResponse(
    val result: TrpcResult? = null,
    val error: TrpcError? = null,
)

@Serializable
data class TrpcError(val message: String? = null, val code: Int? = null)

@Serializable
data class TrpcResult(val data: TrpcData? = null)

@Serializable
data class TrpcData(val json: JsonElement? = null)

@Serializable
data class SeriesDataDto(
    val chapters: List<NextJsChapterDto>? = null,
    val series: SeriesInfoDto? = null,
    val pagination: PaginationDto? = null,
)

@Serializable
data class PaginationDto(
    val totalPages: Int? = null,
    val total: Int? = null,
)

@Serializable
data class SeriesInfoDto(val id: String? = null, val name: String? = null)

@Serializable
data class NextJsChapterDto(
    val no: Float? = null,
    val name: String? = null,
    val date: String? = null,
    val href: String? = null,
)

@Serializable
data class SadScansPageDto(
    val src: String?,
)
