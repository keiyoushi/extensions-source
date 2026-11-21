package eu.kanade.tachiyomi.extension.bn.banglamanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    val id: String,
    val slug: String,
    @SerialName("title_bn")
    val titleBn: String,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    @SerialName("synopsis_bn")
    val synopsisBn: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val genres: List<String>? = null,
    val chapters: List<ChapterDto>? = null,
)

@Serializable
class ChapterDto(
    val id: String,
    val number: Double,
    @SerialName("title_bn")
    val titleBn: String,
    @SerialName("publish_at")
    val publishAt: String,
)

@Serializable
class ChapterPagesDto(
    val pages: List<ChapterPageDto>? = null,
)

@Serializable
class ChapterPageDto(
    val url: String,
)
