package eu.kanade.tachiyomi.extension.en.hypercomic

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BrowseDto(
    val totalpage: Int,
    private val data: List<BrowseItemDto>,
) {
    // An empty list comes back as a single {"cnt": "0"} entry.
    fun toSMangaList(): List<SManga> = data.mapNotNull { it.toSManga() }
}

@Serializable
class BrowseItemDto(
    @SerialName("CONT_IDX") private val id: String? = null,
    @SerialName("TITLE") private val title: String? = null,
    @SerialName("THUMBNAIL") private val thumbnail: String? = null,
) {
    fun toSManga(): SManga? {
        return SManga.create().apply {
            url = "/Webtoon/episode/${id ?: return null}/1/"
            title = this@BrowseItemDto.title!!.trim()
            thumbnail_url = thumbnail?.let { "https://d2gfgyilxd7m0r.cloudfront.net/$it" }
        }
    }
}
