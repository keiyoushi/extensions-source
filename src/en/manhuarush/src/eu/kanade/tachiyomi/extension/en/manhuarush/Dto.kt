package eu.kanade.tachiyomi.extension.en.manhuarush

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable

@Serializable
class MangaDetailsDto(
    val text: String? = null,
    val chapters: List<ChapterDto>,
    val mangadexId: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        description = text
    }
}

@Serializable
class ChapterDto(
    val chapter: String,
    val title: String,
    val createdAt: String? = null,
) {
    fun toSChapter(mangadexId: String): SChapter = SChapter.create().apply {
        url = "/reader/$mangadexId/$chapter"
        name = "Chapter $chapter" + (if (title.isNotEmpty()) " - $title" else "")
        date_upload = manhuaRushDateFormat.tryParse(createdAt)
    }
}

@Serializable
class ReaderDto(
    val imageUrls: List<String>,
) {
    fun toPages(): List<Page> = imageUrls.mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }
}
