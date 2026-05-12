package eu.kanade.tachiyomi.extension.ja.mangameets

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SeriesResponse(
    val data: SeriesData,
    private val included: List<IncludedItem>,
) {
    fun getComics(): List<ComicEntry> {
        val comics = included.filter { it.type == "comic" }
        val images = included.filter { it.type == "image" }

        return comics.mapIndexed { index, comic ->
            ComicEntry(comic.attributes, images[index].attributes)
        }
    }
}

@Serializable
class SeriesData(
    val attributes: SeriesAttributes,
)

@Serializable
class SeriesAttributes(
    @SerialName("total_page") private val totalPage: Int,
    @SerialName("current_page") private val currentPage: Int,
) {
    fun hasNextPage() = currentPage < totalPage
}

@Serializable
class IncludedItem(
    val id: String,
    val type: String,
    val attributes: IncludedAttributes,
)

@Serializable
class IncludedAttributes(
    @SerialName("dir_name") val dirName: String?,
    val title: String?,
    val url: String?,
    val name: String?,
)

@Serializable
class RelationData(
    val id: String,
)

class ComicEntry(
    private val comic: IncludedAttributes,
    private val image: IncludedAttributes,
) {
    fun toSManga() = SManga.create().apply {
        url = comic.dirName!!
        title = comic.title!!
        thumbnail_url = image.url
    }
}

@Serializable
class DetailsResponse(
    private val data: ComicData,
    private val included: List<IncludedItem>,
) {
    fun toSManga() = SManga.create().apply {
        val imageMap = included
            .filter { it.type == "image" }
            .associate { it.id to it.attributes.url }
        val genreMap = included
            .filter { it.type == "comic_genre" }
            .associate { it.id to it.attributes.name }

        title = data.attributes.title
        author = data.attributes.authors?.joinToString()
        artist = data.attributes.authors?.joinToString()
        description = data.attributes.outline
        genre = data.relationships.comicGenre?.data?.id?.let { genreMap[it] }
        status = if (data.attributes.finished == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = data.relationships.thumbnailImage?.data?.id?.let { imageMap[it] }
    }
}

@Serializable
class ComicData(
    val attributes: ComicDataAttributes,
    val relationships: ComicDataRelationships,
)

@Serializable
class ComicDataAttributes(
    val title: String,
    val authors: List<String>?,
    val outline: String?,
    val finished: Boolean?,
)

@Serializable
class ComicDataRelationships(
    @SerialName("comic_genre") val comicGenre: RelationWrapper?,
    @SerialName("thumbnail_image") val thumbnailImage: RelationWrapper?,
)

@Serializable
class RelationWrapper(
    val data: RelationData?,
)

@Serializable
class ChapterResponse(
    val data: List<EpisodeEntry>,
)

@Serializable
class EpisodeEntry(
    val attributes: EpisodeAttributes,
)

@Serializable
class EpisodeAttributes(
    private val title: String?,
    private val volume: String,
    @SerialName("sort_volume") private val sortVolume: Int,
    @SerialName("published_at") private val publishedAt: String?,
) {
    fun toSChapter(dirName: String) = SChapter.create().apply {
        val chapterTitle = if (!title.isNullOrEmpty()) " - $title" else ""
        url = "$dirName/$sortVolume"
        name = "Chapter $volume$chapterTitle"
        date_upload = dateFormat.tryParse(publishedAt)
        chapter_number = sortVolume.toFloat()
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZZZZ", Locale.ROOT)

@Serializable
class ViewerResponse(
    @SerialName("episode_pages") val episodePages: List<EpisodePage>,
)

@Serializable
class EpisodePage(
    @SerialName("order_index") val orderIndex: Int,
    val image: EpisodeImage,
)

@Serializable
class EpisodeImage(
    @SerialName("original_url") val originalUrl: String,
)

@Serializable
class GenreResponse(
    val data: List<GenreData>,
)

@Serializable
class GenreData(
    val attributes: GenreAttributes,
)

@Serializable
class GenreAttributes(
    val name: String,
)
