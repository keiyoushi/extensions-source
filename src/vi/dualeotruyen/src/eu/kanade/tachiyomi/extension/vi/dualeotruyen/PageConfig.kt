package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PageConfig(
    @SerialName("a") val urlAttr: String,
    @SerialName("b") val prefixAttr: String,
    @SerialName("s") val seedAttr: String,
    @SerialName("m") val multiplier: Int,
    @SerialName("c") val increment: Int,
    @SerialName("ci") val imageClass: String,
    @SerialName("cc") val canvasClass: String,
    @SerialName("p") val namePrefix: String,
    @SerialName("k") val key: String,
)
