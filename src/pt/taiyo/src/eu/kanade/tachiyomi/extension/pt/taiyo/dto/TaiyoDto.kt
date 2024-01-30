package eu.kanade.tachiyomi.extension.pt.taiyo.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResponseDto<T>(val result: ResultDto<T>) {
    val data: T = result.data.json
}

@Serializable
data class ResultDto<T>(val data: DataDto<T>)

@Serializable
data class DataDto<T>(val json: T)

@Serializable
data class SearchResultDto(
    val id: String,
    val title: String,
    val coverId: String? = null,
)
