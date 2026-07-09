package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawProjectCategory(
    @SerialName("CateName")
    val categoryName: String,
)
