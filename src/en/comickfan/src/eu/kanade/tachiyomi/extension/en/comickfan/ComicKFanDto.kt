package eu.kanade.tachiyomi.extension.en.comickfan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ComicKFanChapterListResponseDto(
    val data: List<ComicKFanChapterDto>,
)

@Serializable
class ComicKFanChapterDto(
    @SerialName("hash_id") val hashId: String,
    val chapter: String,
    val title: String?,
    @SerialName("group_names") val groupNames: List<String> = emptyList(),
    @SerialName("published_at") val publishedAt: String?,
    @SerialName("created_at") val createdAt: String?,
)
