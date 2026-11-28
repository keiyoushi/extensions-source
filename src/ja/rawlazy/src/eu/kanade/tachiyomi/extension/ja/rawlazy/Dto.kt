package eu.kanade.tachiyomi.extension.ja.rawlazy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AjaxDto(
    @SerialName("mes") val html: String,
    // Only on the paginated lists
    val going: String?,
)

@Serializable
class CategoryDto(
    val id: Int,
    val name: String,
)
