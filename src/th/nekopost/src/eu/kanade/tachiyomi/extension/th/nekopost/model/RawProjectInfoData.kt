package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectInfoData(
    @SerialName("projectId")
    val projectId: String,
    @SerialName("projectName")
    val projectName: String,
    @SerialName("aliasName")
    val aliasName: String,
    @SerialName("website")
    val website: String,
    @SerialName("authorName")
    val authorName: String,
    @SerialName("artistName")
    val artistName: String,
    @SerialName("info")
    val info: String,
    @SerialName("status")
    val status: String,
    @SerialName("flgMature")
    val flgMature: String,
    @SerialName("releaseDate")
    val releaseDate: String,
)
