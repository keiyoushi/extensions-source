package eu.kanade.tachiyomi.extension.es.spicyscan

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlin.time.Instant

@Serializable
class FilterResponseDto(
    private val data: List<MangaDto>,
    private val meta: FilterMetaDto,
) {
    fun toMangasPage() = MangasPage(
        mangas = data.map { it.toSManga() },
        hasNextPage = meta.hasNextPage,
    )
}

@Serializable
class FilterMetaDto(
    @SerialName("current_page") private val currentPage: Int,
    @SerialName("last_page") private val lastPage: Int,
) {
    val hasNextPage get() = currentPage < lastPage
}

@Serializable
class SeriesResponseDto(
    @SerialName("serie") val series: MangaDto,
)

@Serializable
class PagesResponseDto(
    @SerialName("pageches")
    @Serializable(FirstobjOrObj::class)
    private val pages: ChapterImagesDto,
) {
    fun toPageList() = pages.toPageList()
}

@Serializable
class MangaDto(
    private val name: String,
    private val slug: String,
    @SerialName("sinopsis") private val synopsis: String?,
    @SerialName("urlImg") private val thumbnailUrl: String,
    private val stateId: Int?,
    @SerialName("genders") private val genres: List<GenreDto>?,
    private val chapters: List<ChapterDto>?,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = thumbnailUrl
    }

    fun toSMangaDetails() = toSManga().apply {
        description = synopsis
        status = stateId.toSMangaStatus()
        genre = genres?.joinToString(", ") { it.name }
        update_strategy = status.toUpdateStrategy()
    }

    fun toSChapterList() = chapters.orEmpty().map { it.toSChapter(slug) }
}

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val num: Float,
    private val slug: String,
    private val createdAt: String,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "$mangaSlug/$slug"
        name = "Capítulo $num"
        date_upload = Instant.tryParse(createdAt)
        chapter_number = num
    }
}

@Serializable
class ChapterImagesDto(
    @SerialName("urlImg") private val rawImages: String,
) {
    fun toPageList() = rawImages.parseAs<List<String>>().mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }
}

// Get first item only in case of JsonArray

object FirstobjOrObj : JsonTransformingSerializer<ChapterImagesDto>(ChapterImagesDto.serializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = if (element is JsonArray) element[0] else element
}

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
