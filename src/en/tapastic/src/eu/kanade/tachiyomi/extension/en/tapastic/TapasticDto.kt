package eu.kanade.tachiyomi.extension.en.tapastic

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class Field(
    private val thumbnailImage: Map<String, String>,
) {
    val thumbnailUrl: String? get() = thumbnailImage.values.firstOrNull()?.let { "$it.png" }
}

@Serializable
class MangaDto(
    val seriesId: Long,
    val title: String,
    val description: String,
    private val assetProperty: Field,
) {
    val thumbnailUrl: String? get() = assetProperty.thumbnailUrl

    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = this@MangaDto.thumbnailUrl
        description = this@MangaDto.description
        url = "/series/${this@MangaDto.seriesId}"
    }
}

@Serializable
class WrapperContent(
    val items: List<MangaDto>,
)

@Serializable
class Meta(
    val pagination: Pagination,
)

@Serializable
class Pagination(
    val last: Boolean = true,
    @SerialName("has_next")
    val hasNext: Boolean = false,
)

@Serializable
class DataWrapper<T>(
    val `data`: T,
    val meta: Meta? = null,
) {
    val items: List<MangaDto> get() = when (data) {
        is WrapperContent -> data.items
        else -> emptyList()
    }

    fun hasNextPage() = when (data) {
        is ChapterListDto -> data.hasNextPage()
        else -> !(meta?.pagination?.last ?: true)
    }
}

@Serializable
class DetailsWrapper(
    val body: MangaDetailsDto,
) {
    fun toSManga() = SManga.create().apply {
        title = body.title
        thumbnail_url = body.thumbnailUrl
        genre = body.genres.joinToString()
    }
}

@Serializable
class MangaDetailsDto(
    @SerialName("series_title")
    val title: String,
    @SerialName("img_url")
    val thumbnailUrl: String,
    val genres: List<String>,
)

@Serializable
class ChapterListDto(
    val pagination: Pagination,
    val episodes: List<ChapterDto>,
) {
    fun hasNextPage() = pagination.hasNext
}

@Serializable
class ChapterDto(
    val id: Long,
    val title: String,
    @SerialName("publish_date")
    val date: String,
    val unlocked: Boolean,
    val free: Boolean,
) {
    fun toSChapter() = SChapter.create().apply {
        name = if (unlocked || free) title else "🔒 $title "
        date_upload = DATE_FORMAT.tryParse(date)
        url = "/episode/${this@ChapterDto.id}"
    }
    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
