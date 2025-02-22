package eu.kanade.tachiyomi.extension.pt.bruttal

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BruttalHomeDto(
    val list: List<BruttalComicBookDto> = emptyList(),
)

@Serializable
data class BruttalComicBookDto(
    val author: String,
    val illustrator: String,
    @SerialName("image_mobile") val imageMobile: String,
    val keywords: String,
    val seasons: List<BruttalSeasonDto> = emptyList(),
    @SerialName("soon_text") val soonText: String = "",
    val synopsis: String,
    val title: String,
    val url: String,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = this@BruttalComicBookDto.title
        description = synopsis + (if (soonText.isEmpty()) "" else "\n\n$soonText")
        artist = illustrator
        author = this@BruttalComicBookDto.author
        genre = keywords.split(";")
            .map { keyword -> keyword.trim().replaceFirstChar { it.uppercase() } }
            .sorted()
            .joinToString()
        status = SManga.ONGOING
        thumbnail_url = "${Bruttal.BRUTTAL_URL}/" + imageMobile.removePrefix("./")
        url = this@BruttalComicBookDto.url
        initialized = true
    }
}

@Serializable
data class BruttalSeasonDto(
    val alias: String,
    val chapters: List<BruttalChapterDto> = emptyList(),
)

@Serializable
data class BruttalChapterDto(
    val alias: String,
    val images: List<BruttalImageDto> = emptyList(),
    @SerialName("share_title") val shareTitle: String,
    val title: String,
    val url: String,
) {

    fun toSChapter(): SChapter = SChapter.create().apply {
        name = title
        chapter_number = shareTitle
            .removePrefix("Capítulo ")
            .toFloatOrNull() ?: -1f
        url = this@BruttalChapterDto.url
    }
}

@Serializable
data class BruttalImageDto(
    val image: String,
)
