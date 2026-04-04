package eu.kanade.tachiyomi.multisrc.spicytheme

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.tryParse
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.serializer
import java.text.SimpleDateFormat

@Serializable
data class FilterResponseDto(
    val data: List<MangaDto>,
    val meta: FilterMetaDto,
)

@Serializable
data class SeriesResponseDto(
    @SerialName("serie") val series: MangaDto,
)

@Serializable
data class PagesResponseDto(
    val id: Int,
    val num: Float,
    val name: String?,
    val slug: String,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
    @SerialName("pageches")
    @Serializable(FirstobjOrObj::class)
    val pages: ChapterImagesDto,
)

@Serializable
data class FilterMetaDto(
    val total: Int,
    @SerialName("per_page") val perPage: Int,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
data class MangaDto(
    val id: Int,
    val name: String,
    val slug: String,
    val type: String,
    @SerialName("sinopsis") val synopsis: String?,
    @SerialName("urlImg") val thumbnailUrl: String,
    val stateId: Int?,
    val alternativeName: String?,
    val createdAt: String?,
    @SerialName("users_count") val usersCount: Int?,
    @SerialName("chapters_count") val chaptersCount: Int?,
    @SerialName("genders") val genres: List<GenreDto>?,
    val chapters: List<ChapterDto>?,
)

@Serializable
data class GenreDto(
    val name: String,
    val id: Int,
)

@Serializable
data class ChapterDto(
    val id: Int,
    val num: Float,
    val slug: String,
    val createdAt: String,
)

@Serializable
data class ChapterImagesDto(
    @SerialName("urlImg") val rawImages: String,
    val chapterId: Int,
)

// Get first item only in case of JsonArray

object FirstobjOrObj : KSerializer<ChapterImagesDto> {

    override val descriptor = ChapterImagesDto.serializer().descriptor

    override fun deserialize(decoder: Decoder): ChapterImagesDto {
        val el = (decoder as JsonDecoder).decodeJsonElement()
        val obj = if (el is JsonArray) el[0] else el
        return decoder.json.decodeFromJsonElement(ChapterImagesDto.serializer(), el)
    }

    override fun serialize(encoder: Encoder, value: ChapterImagesDto) {
        encoder.encodeSerializableValue(serializer(), value)
    }
}

fun MangaDto.toSManga() = SManga.create().apply {
    url = slug
    title = name
    thumbnail_url = thumbnailUrl
}

fun MangaDto.toSMangaDetails() = this.toSManga().apply {
    description = synopsis
    status = stateId.toSMangaStatus()
    genre = genres?.joinToString(", ") { it.name }
    update_strategy = status.toUpdateStrategy()
}

fun ChapterDto.toSChapter(mangaSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
    url = "$mangaSlug/$slug"
    name = "Capítulo $num"
    date_upload = dateFormat.tryParse(createdAt)
    chapter_number = num
}

fun FilterResponseDto.toMangasPage() = MangasPage(
    mangas = data.map { it.toSManga() },
    hasNextPage = meta.currentPage < meta.lastPage,
)

private fun Int?.toSMangaStatus(): Int = when (this) {
    1 -> SManga.ONGOING
    2 -> SManga.ON_HIATUS
    3, 5 -> SManga.CANCELLED
    4 -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private fun Int.toUpdateStrategy(): UpdateStrategy = when (this) {
    SManga.COMPLETED -> UpdateStrategy.ONLY_FETCH_ONCE
    else -> UpdateStrategy.ALWAYS_UPDATE
}
