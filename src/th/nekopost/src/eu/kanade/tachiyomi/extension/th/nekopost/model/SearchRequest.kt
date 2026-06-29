package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
class PagingInfo(
    val pageNo: Int,
    val pageSize: Int,
)

@Serializable
class SearchRequest(
    val keyword: String,
    val status: Int,
    val paging: PagingInfo,
)

@Serializable
class UpdatesRequest(
    val type: String,
    val paging: PagingInfo,
)
