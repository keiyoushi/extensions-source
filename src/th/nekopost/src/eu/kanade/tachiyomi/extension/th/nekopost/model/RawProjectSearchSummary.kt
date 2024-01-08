package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RawProjectSearchSummary(
    val projectId: Int,
    val projectName: String,
    val projectType: String,
    @SerialName("STATUS")
    val status: Int,
    val noChapter: Int,
    val coverVersion: Int,
    val info: String,
    val views: Int,
    @SerialName("lastUpdate")
    val lastUpdateDate: String,
)
