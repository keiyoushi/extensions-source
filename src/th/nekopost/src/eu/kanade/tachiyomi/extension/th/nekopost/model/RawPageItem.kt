package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawPageItem(
    @SerialName("pageName")
    val pageName: String? = null,
    @SerialName("fileName")
    val fileName: String? = null,
    @SerialName("pageNo")
    val pageNo: Int,
)
