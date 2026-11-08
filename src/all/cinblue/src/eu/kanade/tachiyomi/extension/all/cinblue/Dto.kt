package eu.kanade.tachiyomi.extension.all.cinblue

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class SearchDto(
    @SerialName("num_pages") val numPages: Int,
    val result: List<GalleryDto>,
)

@Serializable
class GalleryDto(
    private val id: Int,
    private val title: TitleDto,
    private val cover: ImageDto? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@GalleryDto.title.main
        thumbnail_url = cover?.t
    }
}

@Serializable
class BookDto(
    private val id: Int,
    private val title: TitleDto,
    private val images: ImagesDto,
    private val scanlator: String? = null,
    @SerialName("upload_date") private val uploadDate: JsonPrimitive? = null,
    private val tags: List<TagDto> = emptyList(),
    @SerialName("num_pages") private val numPages: Int,
) {
    private fun tags(type: String) = tags.filter { it.type == type }.map { it.name }

    fun isIn(language: String) = language in tags("language")

    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@BookDto.title.main
        thumbnail_url = images.cover?.t
        artist = tags("artist").joinToString().ifEmpty { null }
        author = artist ?: tags("group").joinToString().ifEmpty { null }
        genre = tags("tag").joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Parodies", tags("parody"))
            .field("Characters", tags("character"))
            .field("Groups", tags("group"))
            .field("Languages", tags("language"))
            .field("Categories", tags("category"))
            .field("Pages", numPages.toString())
            .altTitles(this@BookDto.title.others)
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toSChapter() = SChapter.create().apply {
        url = id.toString()
        name = "Chapter"
        scanlator = this@BookDto.scanlator?.ifBlank { null }
        // Seconds for the galleries the site has just fetched, milliseconds in a string for the others
        date_upload = uploadDate?.content?.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000 else it } ?: 0L
    }

    fun toPages() = images.pages.mapIndexed { index, image -> Page(index, imageUrl = image.t) }
}

@Serializable
class TitleDto(
    private val english: String? = null,
    private val japanese: String? = null,
    private val pretty: String? = null,
) {
    private val all get() = listOfNotNull(english, japanese, pretty).filter { it.isNotBlank() }.distinct()

    val main get() = all.first()

    val others get() = all.drop(1)
}

@Serializable
class ImagesDto(
    val cover: ImageDto? = null,
    val pages: List<ImageDto>,
)

@Serializable
class ImageDto(
    val t: String,
)

@Serializable
class TagDto(
    val type: String,
    val name: String,
)
