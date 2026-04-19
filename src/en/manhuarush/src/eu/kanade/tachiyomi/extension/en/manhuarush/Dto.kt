package eu.kanade.tachiyomi.extension.en.manhuarush

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDetailsDto(
    @SerialName("text") private val text: String? = null,
    @SerialName("chapters") val chapters: List<ChapterDto>,
    @SerialName("mangadexId") val mangadexId: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        description = text
    }
}

@Serializable
class ChapterDto(
    @SerialName("chapter") private val chapter: String,
    @SerialName("title") private val title: String? = null,
    @SerialName("createdAt") private val createdAt: String? = null,
) {
    fun toSChapter(mangadexId: String): SChapter = SChapter.create().apply {
        url = "/reader/$mangadexId/$chapter"
        name = "Chapter $chapter" + (if (!title.isNullOrEmpty()) " - $title" else "")
        date_upload = manhuaRushDateFormat.tryParse(createdAt)
    }
}

@Serializable
class ReaderDto(
    @SerialName("imageUrls") private val imageUrls: List<String>,
) {
    fun toPages(): List<Page> = imageUrls.mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }
}
