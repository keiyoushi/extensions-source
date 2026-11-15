package eu.kanade.tachiyomi.extension.en.pornhwa18

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesDto(
    private val id: Int,
    private val title: String,
    val slug: String,
    private val synopsis: String? = null,
    private val alter: String? = null,
    private val poster: String? = null,
    private val type: String? = null,
    private val status: String? = null,
    val chapters: List<ChapterDto> = emptyList(),
    @SerialName("taxonomy_relation") private val taxonomies: List<TaxonomyRelationDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = this@SeriesDto.title
        // The site's own covers on this IP host are dead; the same files are served by manhwa18.com
        thumbnail_url = poster?.replace("/188.165.221.196/", "/manhwa18.com/")
        memo = buildJsonObject { put("slug", slug) }
    }

    fun toSMangaDetails() = toSManga().apply {
        val tax = taxonomies.map { it.taxonomy }
        author = tax.filter { it.type == "author" }.joinToString { it.name }.ifEmpty { null }
        artist = tax.filter { it.type == "artist" }.joinToString { it.name }.ifEmpty { null }
        genre = (listOfNotNull(type) + tax.filter { it.type == "genre" }.map { it.name }).joinToString()
        description = buildString {
            synopsis?.takeIf(String::isNotBlank)?.let { append(it) }
            alter?.takeIf(String::isNotBlank)?.let {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles: ", it)
            }
        }
        status = when (this@SeriesDto.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            "on-hold" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    fun toSChapters() = chapters.map { it.toSChapter(slug) }
}

@Serializable
class TaxonomyRelationDto(val taxonomy: TaxonomyDto)

@Serializable
class TaxonomyDto(
    val name: String,
    val type: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val chapter: Float,
    @SerialName("created_at") private val createdAt: String? = null,
    val images: Map<String, ImageDto> = emptyMap(),
) {
    fun toSChapter(seriesSlug: String) = SChapter.create().apply {
        val number = chapter.toString().removeSuffix(".0")
        url = id.toString()
        name = "Chapter $number"
        chapter_number = chapter
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject {
            put("slug", seriesSlug)
            put("chapter", number)
        }
    }
}

@Serializable
class ImageDto(val src: String)

@Serializable
class ReaderDto(val data: SeriesDto)
