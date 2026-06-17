package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class EditorProject(
    @SerialName("pid")
    val pid: Int,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("projectType")
    val projectType: String,
    @SerialName("status")
    val status: Int,
    @SerialName("aliasName")
    val aliasName: String? = null,
    @SerialName("website")
    val website: String? = null,
    @SerialName("authorName")
    val authorName: String? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("coverVersion")
    val coverVersion: Int? = null,
)
