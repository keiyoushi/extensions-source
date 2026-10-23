package eu.kanade.tachiyomi.extension.es.lectorxd

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ChapterListProps(
    private val mangaType: String,
    private val mangaSlug: String,
    private val chapters: List<ChapterDto>,
) {
    fun toSChapterList(): List<SChapter> = chapters
        .map { it.toSChapter(mangaType, mangaSlug) }
        .distinctBy { it.url }
}

@Serializable
class ChapterDto(
    private val chapter: String,
    private val groupId: Int? = null,
    private val publishAt: String? = null,
) {
    fun toSChapter(mangaType: String, mangaSlug: String) = SChapter.create().apply {
        name = "Capítulo $chapter"
        url = "/$mangaType/$mangaSlug/leer/$chapter" + (groupId?.let { "?g=$it" } ?: "")
        date_upload = Instant.tryParse(publishAt)
    }
}
