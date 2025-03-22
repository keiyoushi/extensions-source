package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import kotlin.math.ceil

@Serializable
data class MangaDto(
    val id: Int,
    val originalName: String,
    val otherName: String,
    val description: String,
    val status: Int,
    val thumbnail: String,
    val type: String,
    val lastUpdate: String,
    val totalView: Int? = null,
    val totalFollow: Int? = null,
    val likeCount: Int? = null,
    val team: List<TagDto>,
    val origin: List<TagDto>,
    val author: List<TagDto>,
    val tag: List<TagDto>,
    val couple: List<TagDto>,
    val lastChapter: ChapterDto? = null,
    val chapters: List<ChapterDto>? = null,
) {
    fun toSManga(storageUrl: String): SManga = SManga.create().apply {
        val dto = this@MangaDto
        url = "/manga/${dto.id}"
        title = dto.originalName
        author = dto.author.joinToString(", ") { author -> author.name }
        val descElem = Jsoup.parseBodyFragment(dto.description).select("p")
            .joinToString("\n") { it.wholeText() }.trim()
        description = if (dto.otherName.isNotEmpty()) {
            "Tên khác: ${dto.otherName}\n\n" + descElem
        } else {
            descElem
        }

        genre = dto.tag.joinToString(", ") { tag -> tag.name }
        status = when (dto.status) {
            1 -> SManga.UNKNOWN // "Chưa ra mắt" -> Not released
            2 -> SManga.COMPLETED
            3 -> SManga.UNKNOWN // "Sắp ra mắt" -> Upcoming
            4 -> SManga.ONGOING
            5 -> SManga.CANCELLED // "Ngừng dịch" -> source not translating it anymomre
            6 -> SManga.ON_HIATUS
            7 -> SManga.CANCELLED // "Ngừng xuất bản" -> No more publications
            else -> SManga.UNKNOWN
        }
        thumbnail_url = "$storageUrl/" + dto.thumbnail
        initialized = true
    }
}

@Serializable
data class MangaListDto(
    val result: List<MangaDto>,
    val resultCount: Int,
) {
    fun toMangasPage(currentPage: Float = 1f, storageUrl: String): MangasPage {
        val dto = this@MangaListDto
        return MangasPage(
            dto.result.map { it.toSManga(storageUrl) },
            currentPage + 1f <= ceil(dto.resultCount.toFloat() / 20f),
        )
    }
}
