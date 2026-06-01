package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RawProjectSearchSummary(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("status")
    val status: Int,
    @SerialName("projectType")
    val projectType: String,
    @SerialName("coverVersion")
    val coverVersion: Int,
)
