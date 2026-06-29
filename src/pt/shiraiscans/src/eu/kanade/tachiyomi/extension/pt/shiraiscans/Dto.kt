package eu.kanade.tachiyomi.extension.pt.shiraiscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Dto(
    @SerialName("url_base") val urlBase: String,
)
