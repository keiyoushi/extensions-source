package eu.kanade.tachiyomi.multisrc.zing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DecodeImagesDto(
    @SerialName("mes") val html: String,
    val going: Int,
    @SerialName("img_index") val imgIndex: Int,
)

@Serializable
class ZingConfigDto(
    @SerialName("home_url") val homeUrl: String,
    val nonce: String,
)
