package eu.kanade.tachiyomi.extension.en.eahentai

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

private const val IMAGE_URL = "https://i.eahentai.com/file/ea-gallery/"

// Older galleries sit in folders named after them, with spaces and brackets: the image host wants every part form-encoded
fun imageUrl(uri: String) = IMAGE_URL + uri.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }

@Serializable
class AlbumListDto(
    private val items: List<AlbumDto>,
    private val totalPages: Int,
) {
    fun toMangasPage(page: Int) = MangasPage(items.map { it.toSManga() }, page < totalPages)
}

@Serializable
class AlbumDto(
    @SerialName("albumID") private val albumId: Int,
    val addDt: String,
    private val thumbnailUri: String,
    private val title: String,
    private val tags: String? = null,
    private val characters: String? = null,
    private val from: String? = null,
    private val author: String? = null,
    private val albumType: String? = null,
    private val aiDescription: String? = null,
    val images: List<ImageDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = albumId.toString()
        title = this@AlbumDto.title.trim()
        thumbnail_url = imageUrl(thumbnailUri)
    }

    fun toDetailsSManga() = toSManga().apply {
        author = this@AlbumDto.author.splitValues().joinToString().ifEmpty { null }
        genre = (albumType.splitValues() + tags.splitValues()).joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(aiDescription)
            .field("Parodies", from.splitValues())
            .field("Characters", characters.splitValues())
            .build()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    private fun String?.splitValues() = orEmpty().split("|").map { it.trim() }.filter { it.isNotEmpty() }
}

@Serializable
class ImageDto(
    val imageUri: String,
    val sort: Int,
)
