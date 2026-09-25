package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.Serializable

@Serializable
class PaginatedResponseDto<T : EntityDto>(
    val data: List<T> = emptyList(),
    val meta: PaginationStateDto,
)

@Serializable
class ResponseDto<T : EntityDto>(
    val data: T? = null,
)

@Serializable
class PaginationStateDto(
    val limit: Int = 0,
    val offset: Int = 0,
    val total: Int = 0,
) {
    val hasNextPage: Boolean
        get() = limit + offset < total
}
