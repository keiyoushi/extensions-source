package eu.kanade.tachiyomi.multisrc.mmlook

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class ResponseDto(val data: List<ChapterDto>)

@Serializable
class ChapterDto(
    private val chapterid: String,
    private val chaptername: String,
) {
    fun toSChapter(mangaId: String) = SChapter.create().apply {
        url = "$mangaId/$chapterid"
        name = chaptername
    }
}
