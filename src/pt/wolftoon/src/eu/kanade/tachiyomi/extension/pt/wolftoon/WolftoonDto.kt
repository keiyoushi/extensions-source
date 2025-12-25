package eu.kanade.tachiyomi.extension.pt.wolftoon

import eu.kanade.tachiyomi.extension.pt.wolftoon.Wolftoon.Companion.DATE_FORMAT
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.Normalizer

@Serializable
class MangaDto(
    val id: String,
    val title: String,
    val cover: String,
    val status: String,
    val genres: List<String>,
    val synopsis: String,
    val type: String,
    val author: String,
    @SerialName("updated_at")
    private val updatedAt: String,
    val rating: Float,
    val views: Long,
) {
    fun toSManga() = SManga.create().apply {
        this.title = this@MangaDto.title
        this.thumbnail_url = this@MangaDto.cover
        this.genre = (this@MangaDto.genres + listOf(type)).joinToString()
        this.description = this@MangaDto.synopsis
        this.author = this@MangaDto.author
        this.status = when (this@MangaDto.status.lowercase()) {
            "em andamento" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        initialized = true
        url = "/manga/${title.toSlug()}#${this@MangaDto.id}"
    }

    private fun String.toSlug(): String {
        val noDiacritics = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return noDiacritics.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }
    fun updatedAt(): Long = DATE_FORMAT.tryParse(updatedAt)
}

@Serializable
class ChapterDto(
    private val id: String,
    @SerialName("title_id")
    val titleId: String,
    @SerialName("chapter_number")
    private val number: Float,
    @SerialName("created_at")
    private val createdAt: String,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        name = "Capítulo $number"
        chapter_number = number
        date_upload = DATE_FORMAT.tryParse(createdAt)
        url = "/read/$titleId/$number#$id"
    }
}

@Serializable
class PageDto(
    val id: String,
    @SerialName("title_id")
    val titleId: String,
    private val images: List<String> = emptyList(),
) {
    fun toPageList(): List<Page> = images.mapIndexed { index, imageUrl ->
        Page(index, imageUrl = imageUrl)
    }
}
