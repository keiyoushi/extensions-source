package eu.kanade.tachiyomi.extension.en.warforrayuba.dto

import kotlinx.serialization.Serializable

@Serializable
data class RoundDto(
    val title: String,
    val description: String,
    val artist: String,
    val author: String,
    val cover: String,
    val chapters: Map<Int, ChapterDto>,
)
