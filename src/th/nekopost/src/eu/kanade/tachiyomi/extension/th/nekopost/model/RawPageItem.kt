package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class RawPageItem(
    val pageName: String? = null,
    val fileName: String? = null,
    val height: Int,
    val pageNo: Int,
    val width: Int,
)
