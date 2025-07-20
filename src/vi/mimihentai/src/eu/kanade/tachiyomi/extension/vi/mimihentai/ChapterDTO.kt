package eu.kanade.tachiyomi.extension.vi.mimihentai

import kotlinx.serialization.Serializable

@Serializable
class ChapterDTO(
    val id: Long,
    val title: String,
    val createdAt: String,
)
