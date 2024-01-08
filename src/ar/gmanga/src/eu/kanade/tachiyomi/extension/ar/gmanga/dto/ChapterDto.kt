package eu.kanade.tachiyomi.extension.ar.gmanga.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val id: Int,
    val chapter: Float,
    val volume: Int,
    val title: String,
    @SerialName("time_stamp") val timestamp: Long,
)
