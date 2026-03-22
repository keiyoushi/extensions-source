package eu.kanade.tachiyomi.extension.ja.mokuro

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class LibraryDto(
    val series: List<SeriesDto>,
)

@Serializable
class SeriesDto(
    val name: String,
    val path: String,
    private val cover: String? = null,
    val volumes: List<VolumeDto>,
) {
    fun toSManga(apiBaseUrl: String): SManga = SManga.create().apply {
        title = name
        url = path
        thumbnail_url = cover?.let {
            apiBaseUrl.toHttpUrl().newBuilder()
                .addPathSegment("cover")
                .addQueryParameter("path", it)
                .build()
                .toString()
        }
    }
}

@Serializable
class VolumeDto(
    val name: String,
    private val cover: String? = null,
) {
    fun toSChapter(series: SeriesDto): SChapter = SChapter.create().apply {
        name = this@VolumeDto.name
        chapter_number = parseChapterNumber(this@VolumeDto.name)
        url = series.path + "|" + this@VolumeDto.name
    }

    private fun parseChapterNumber(name: String): Float = chapterNumberRegex.findAll(name).lastOrNull()?.value?.toFloatOrNull() ?: -1f
}

@Serializable
class MokuroDto(
    val pages: List<MokuroPageDto>,
)

@Serializable
class MokuroPageDto(
    @SerialName("img_path") val imgPath: String,
)

private val chapterNumberRegex = """(\d+(\.\d+)?)""".toRegex()
