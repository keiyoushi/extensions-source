package eu.kanade.tachiyomi.extension.en.alandal

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ResponseDto<T>(
    val data: ResultDto<T>,
) {
    @Serializable
    class ResultDto<T>(
        val series: T,
    )
}

@Serializable
class SearchSeriesDto(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    val data: List<SearchEntryDto>,
) {
    @Serializable
    class SearchEntryDto(
        val name: String,
        val slug: String,
        val cover: String,
    ) {
        fun toSManga(): SManga = SManga.create().apply {
            title = name
            url = "/series/$slug"
            thumbnail_url = cover
        }
    }
}

@Serializable
class MangaDetailsDto(
    val name: String,
    val summary: String,
    val status: NamedObject,
    val genres: List<NamedObject>,
    val creators: List<NamedObject>,
    val cover: String,
) {
    @Serializable
    class NamedObject(
        val name: String,
        val type: String? = null,
    )

    fun toSManga(): SManga = SManga.create().apply {
        title = name
        thumbnail_url = cover
        description = Jsoup.parseBodyFragment(summary).text()
        genre = genres.joinToString { it.name }
        author = creators.filter { it.type!! == "author" }.joinToString { it.name }
        status = this@MangaDetailsDto.status.name.parseStatus()
    }

    private fun String.parseStatus(): Int = when (this.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}

@Serializable
class ChapterResponseDto(
    val data: List<ChapterDto>,
) {
    @Serializable
    class ChapterDto(
        val name: String,
        @SerialName("published_at") val published: String,
        val access: Boolean,
    ) {
        fun toSChapter(slug: String): SChapter = SChapter.create().apply {
            val prefix = if (access) "" else "[LOCKED] "
            name = "${prefix}Chapter ${this@ChapterDto.name}"
            date_upload = try {
                dateFormat.parse(published)!!.time
            } catch (_: ParseException) {
                0L
            }
            url = "$slug/${this@ChapterDto.name}"
        }

        companion object {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
        }
    }
}

@Serializable
class PagesResponseDto(
    val data: PagesDataDto,
) {
    @Serializable
    class PagesDataDto(
        val chapter: PagesChapterDto,
    ) {
        @Serializable
        class PagesChapterDto(
            val chapter: PagesChapterImagesDto,
        ) {
            @Serializable
            class PagesChapterImagesDto(
                val pages: List<String>,
            )
        }
    }
}
