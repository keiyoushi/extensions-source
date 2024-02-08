package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchRequest(
    val keyword: String,
    val pageNo: Int,
)
