package eu.kanade.tachiyomi.extension.all.mangatoon

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DataDto<T>(val data: T? = null)

@Serializable
class DetailDto(
    private val title: String,
    @SerialName("content_description") private val contentDescription: String? = null,
    private val author: AuthorDto? = null,
    private val tags: List<TagDto> = emptyList(),
    @SerialName("is_end") private val isEnd: Boolean,
    @SerialName("image_url") private val imageUrl: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@DetailDto.title
        author = this@DetailDto.author?.name?.trim()
        description = MarkdownDescription.Builder()
            .description(contentDescription)
            .build()
        genre = tags.joinToString { it.name }.ifEmpty { null }
        status = if (isEnd) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = imageUrl?.replace("http://", "https://")
    }
}

@Serializable
class AuthorDto(val name: String)

@Serializable
class TagDto(val name: String)

@Serializable
class EpisodeDto(
    private val id: Long,
    private val title: String,
    private val weight: Float,
    @SerialName("open_at") private val openAt: Long,
    @SerialName("is_fee") val isFee: Boolean,
) {
    fun toSChapter(urlLang: String, contentId: String) = SChapter.create().apply {
        url = "/$urlLang/watch/$contentId/$id"
        name = title
        chapter_number = weight
        date_upload = openAt * 1000
    }
}

@Serializable
class PictureDto(private val url: String? = null) {
    // The site's reader loads the plain JPEG copy of each encrypted WebP file
    val imageUrl: String?
        get() = url?.replace("/encrypted/", "/watermark/")
            ?.replace(".webp", ".jpg")
            ?.replace(".e.pic.", "-e-pic.")
            ?.replace(".c.pic.", "-c-pic.")
            ?.replace("http://", "https://")
}
