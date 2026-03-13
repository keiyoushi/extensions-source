package eu.kanade.tachiyomi.extension.th.reborntrans.model

import kotlinx.serialization.Serializable

@Serializable
data class WpSearchResult(
    val id: Int,
    val title: String,
    val url: String,
    val subtype: String,
)
