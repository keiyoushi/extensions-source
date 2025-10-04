package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class PagingInfo(
    val pageNo: Int,
    val pageSize: Int,
)

@Serializable
data class SearchRequest(
    val keyword: String,
    val status: Int,
    val paging: PagingInfo,
)
