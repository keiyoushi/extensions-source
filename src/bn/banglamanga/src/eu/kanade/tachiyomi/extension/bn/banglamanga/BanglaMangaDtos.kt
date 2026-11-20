package eu.kanade.tachiyomi.extension.bn.banglamanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaDto(
    val id: String,
    val slug: String,
    @SerialName("title_bn")
    val titleBn: String,
    @SerialName("title_en")
    val titleEn: String,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    @SerialName("synopsis_bn")
    val synopsisBn: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val genres: List<String>? = null,
    @SerialName("release_year")
    val releaseYear: Int? = null,
)

@Serializable
data class MangaDetailsDto(
    val id: String,
    val slug: String,
    @SerialName("title_bn")
    val titleBn: String,
    @SerialName("title_en")
    val titleEn: String,
    val author: String? = null,
    val artist: String? = null,
    val status: String? = null,
    @SerialName("synopsis_bn")
    val synopsisBn: String? = null,
    @SerialName("cover_url")
    val coverUrl: String? = null,
    val genres: List<String>? = null,
    @SerialName("release_year")
    val releaseYear: Int? = null,
    val chapters: List<ChapterDto>? = null,
)

@Serializable
data class ChapterDto(
    val id: String,
    val number: Double,
    @SerialName("title_bn")
    val titleBn: String,
    val status: String? = null,
    val volume: Int? = null,
    @SerialName("number_end")
    val numberEnd: Double? = null,
    @SerialName("publish_at")
    val publishAt: String,
    val views: Int? = null,
    @SerialName("reading_mode")
    val readingMode: String? = null,
    val credits: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class ChapterPagesDto(
    val pages: List<ChapterPageDto>? = null,
)

@Serializable
data class ChapterPageDto(
    val url: String,
    val name: String? = null,
    val w: Int? = null,
    val h: Int? = null,
)
