package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

val DATE_FORMATTER = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
}

val CHAPTER_NUMBER_REGEX = Regex("""[+\-]?([0-9]*[\.])?[0-9]+""", RegexOption.IGNORE_CASE)

@Serializable
data class ChapterDto(
    val id: Int,
    val name: String,
    val date: String? = null,
    val mangaID: Int? = null,
    val maxID: Int? = null,
    val likeCount: Int? = null,
) {
    fun toSChapter(teams: String): SChapter = SChapter.create().apply {
        val dto = this@ChapterDto
        url = "/read/${dto.mangaID}/${dto.id}"
        name = dto.name
        if (!dto.date.isNullOrEmpty()) {
            date_upload = runCatching {
                DATE_FORMATTER.parse(dto.date)?.time
            }.getOrNull() ?: 0L
        }

        val match = CHAPTER_NUMBER_REGEX.findAll(dto.name)
        chapter_number = if (match.count() > 1 && dto.name.lowercase().startsWith("vol")) {
            match.elementAt(1)
        } else {
            match.elementAtOrNull(0)
        }?.value?.toFloat() ?: -1f
        scanlator = teams
    }
}

@Serializable
data class ReadResponseDto(
    val listChapter: List<ChapterDto>,
    val chapterInfo: ChapterDto,
    val url: List<String>,
) {
    fun toPageList(): List<Page> = this@ReadResponseDto
        .url
        .mapIndexed { index, url -> Page(index, imageUrl = "https://storage.yurineko.my" + url) }
}
