package eu.kanade.tachiyomi.extension.ko.mangatoki

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import kotlin.time.Instant

@Serializable
class MangaListDto(
    private val posts: List<MangaDto>,
    private val total: Int,
) {
    fun toMangasPage(page: Int, imageUrl: HttpUrl) = MangasPage(posts.map { it.toSManga(imageUrl) }, page * PAGE_SIZE < total)
}

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val image: String?,
    private val images: List<String> = emptyList(),
    private val tags: List<String> = emptyList(),
    @SerialName("created_at") private val createdAt: String?,
) {
    fun toSManga(imageUrl: HttpUrl) = SManga.create().apply {
        url = id.toString()
        title = this@MangaDto.title
        thumbnail_url = image?.let { imageUrl.resolve(it)!!.toString() }
    }

    fun toDetails(imageUrl: HttpUrl) = toSManga(imageUrl).apply {
        val artists = tagValues("artist")
        author = artists.ifEmpty { tagValues("group") }.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }
        genre = tags.mapNotNull { tag ->
            val (namespace, value) = tag.split(':', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull tag.readable()
            when (namespace) {
                "female" -> "${value.readable()} ♀"
                "male" -> "${value.readable()} ♂"
                in DESCRIBED_NAMESPACES -> null
                else -> value.readable()
            }
        }.joinToString().ifEmpty { null }
        description = MarkdownDescription.Builder()
            .field("Series", tagValues("series"))
            .field("Characters", tagValues("character"))
            .field("Groups", tagValues("group"))
            .field("Type", tagValues("type"))
            .field("Pages", images.size.takeIf { it > 0 }?.toString())
            .build()
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

    fun toChapter() = SChapter.create().apply {
        url = id.toString()
        name = "Chapter"
        scanlator = tagValues("group").joinToString().ifEmpty { null }
        date_upload = Instant.tryParse(createdAt)
    }

    fun toPages(imageUrl: HttpUrl) = images.mapIndexed { index, path -> Page(index, imageUrl = imageUrl.resolve(path)!!.toString()) }

    private fun tagValues(namespace: String) = tags.filter { it.startsWith("$namespace:") }.map { it.substringAfter(':').readable() }
}

private fun String.readable() = replace('_', ' ')

private val DESCRIBED_NAMESPACES = setOf("artist", "group", "series", "character", "type")

const val PAGE_SIZE = 30
