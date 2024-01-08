package eu.kanade.tachiyomi.extension.zh.sixmh

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class QixiChapterDto(private val id: String, private val name: String) {
    fun toSChapter(path: String) = SChapter.create().apply {
        url = "$path$id.html"
        name = this@QixiChapterDto.name
    }
}

@Serializable
class QixiDataDto(val list: List<QixiChapterDto>)

@Serializable
class QixiResponseDto(val data: QixiDataDto)
