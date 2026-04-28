package eu.kanade.tachiyomi.extension.es.plottwistnofansub

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterApiResponse(
    val manga: List<VolumeDto>,
)

@Serializable
class VolumeDto(
    val chapters: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") val chapterName: String,
    @SerialName("chapter_name_extend") val chapterNameExtend: String = "",
    @SerialName("chapter_slug") val chapterSlug: String,
    val date: String,
)
