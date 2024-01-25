package eu.kanade.tachiyomi.extension.all.komga.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageWrapperDto<T>(
    val content: List<T>,
    val empty: Boolean,
    val first: Boolean,
    val last: Boolean,
    val number: Long,
    val numberOfElements: Long,
    val size: Long,
    val totalElements: Long,
    val totalPages: Long,
)
