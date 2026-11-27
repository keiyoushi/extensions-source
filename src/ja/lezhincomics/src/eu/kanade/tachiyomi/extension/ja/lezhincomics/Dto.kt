package eu.kanade.tachiyomi.extension.ja.lezhincomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.jsoup.Jsoup

@Serializable
class ResultDto<T>(
    val results: T,
)

@Serializable
class PageDto<T>(
    val data: List<T>,
    val pagination: PaginationDto,
)

@Serializable
class PaginationDto(
    @SerialName("current_page") private val currentPage: Int? = null,
    @SerialName("last_page") private val lastPage: Int? = null,
    val next: String? = null,
    @SerialName("has_more_pages") private val hasMorePages: Boolean? = null,
) {
    val hasNextPage get() = hasMorePages ?: (currentPage!! < lastPage!!)
}

@Serializable
class GenreDto(
    @SerialName("hash_id") val hashId: String,
    val name: String,
)

@Serializable
class RankingDto(
    val comic: ComicItemDto,
)

@Serializable
class ComicItemDto(
    @SerialName("title_id") private val titleId: String? = null,
    private val id: String,
    private val name: String,
    @SerialName("cover_thumbnail_url") private val thumbnail: String? = null,
    private val author: List<NamedDto>? = null,
    private val genre: List<NamedDto>? = null,
    private val tag: List<NamedDto>? = null,
) {
    fun toSManga() = SManga.create().apply {
        // Ranking and search items carry the title id as `id`; serialization items as `title_id`
        url = titleId ?: id
        title = name
        thumbnail_url = thumbnail
        author = this@ComicItemDto.author?.joinToString { it.name }?.ifEmpty { null }
        genre = genres(this@ComicItemDto.genre.orEmpty(), tag.orEmpty())
    }
}

@Serializable
class ComicDto(
    private val name: String,
    private val description: String? = null,
    @SerialName("cover_image_url") private val cover: String? = null,
    private val copyright: String? = null,
    private val author: ListDto<NamedDto>,
    private val tag: ListDto<NamedDto>,
    private val genre: ListDto<NamedDto>,
    private val publisher: NamedDto? = null,
) {
    fun toSManga(manga: SManga) = manga.apply {
        title = name
        thumbnail_url = cover
        author = this@ComicDto.author.data.joinToString { it.name }.ifEmpty { null }
        genre = genres(this@ComicDto.genre.data, tag.data)
        status = if (tag.data.any { it.name == "完結" }) SManga.COMPLETED else SManga.UNKNOWN
        description = MarkdownDescription.Builder()
            .description(this@ComicDto.description?.let { Jsoup.parseBodyFragment(it).body() })
            .field("出版社", publisher?.name)
            .field("著作権", copyright)
            .build()
    }
}

private fun genres(genres: List<NamedDto>, tags: List<NamedDto>) = (genres + tags).map { it.name }.distinct().ifEmpty { null }?.joinToString()

@Serializable
class ListDto<T>(
    val data: List<T>,
)

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class ChapterGroupDto(
    val chapters: List<EpisodeDto>,
)

@Serializable
class EpisodeDto(
    @SerialName("hash_id") private val hashId: String,
    private val name: String,
    @SerialName("viewable_type") private val viewableType: String,
    @SerialName("is_bought") private val isBought: Boolean,
) {
    val isLocked get() = viewableType != "free" && !isBought

    fun toSChapter(titleId: String) = SChapter.create().apply {
        url = "$titleId/chapter/$hashId"
        name = if (isLocked) "🔒 ${this@EpisodeDto.name}" else this@EpisodeDto.name
    }
}

@Serializable
class VolumeDto(
    @SerialName("hash_id") private val hashId: String,
    private val name: String,
    @SerialName("viewable_type") private val viewableType: String,
    @SerialName("is_bought") private val isBought: Boolean,
    @SerialName("is_allow_trial") private val isAllowTrial: Boolean,
) {
    val isLocked get() = viewableType != "free" && !isBought

    // A locked volume opens its free preview pages
    val isTrial get() = isLocked && isAllowTrial

    fun toSChapter(titleId: String, titleName: String) = SChapter.create().apply {
        url = "$titleId/volume/$hashId"
        // The app hides a chapter name equal to the title
        val volumeName = this@VolumeDto.name.takeIf { it != titleName } ?: "1巻"
        name = if (isLocked && !isTrial) "🔒 $volumeName" else volumeName
        scanlator = if (isTrial) "試し読み" else null
    }
}

@Serializable
class ComicPageDto(
    val comicDetailData: JsonObject,
)

@Serializable
class ErrorDto(
    val message: String,
)

@Serializable
class ViewerDto(
    @SerialName("image_paths") val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    @SerialName("image_path") val url: String,
)
