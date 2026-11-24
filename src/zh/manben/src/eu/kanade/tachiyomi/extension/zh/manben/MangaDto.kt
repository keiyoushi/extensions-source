package eu.kanade.tachiyomi.extension.zh.manben

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    @SerialName("Url") private val url: String,
    @SerialName("Title") private val title: String,
    @SerialName("BigPic") private val cover: String?,
    @SerialName("Author") private val authors: List<String>?,
    @SerialName("TagList") private val tags: List<String>?,
    @SerialName("Status") private val status: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = this@MangaDto.url
        title = this@MangaDto.title
        thumbnail_url = cover
        author = authors?.joinToString()?.ifEmpty { null }
        genre = tags?.joinToString()?.ifEmpty { null }
        status = when (this@MangaDto.status) {
            "连载中" -> SManga.ONGOING
            "已完结" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
