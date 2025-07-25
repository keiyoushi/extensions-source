package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

@Serializable
class ResultDto<T>(
    val result: T,
)

typealias ListChapter = ResultDto<ResultChapter>

@Serializable
class ChapterDto(
    private val numberChapter: String,
    private val stringUpdateTime: String,
) {
    fun toSChapter(slug: String): SChapter = SChapter.create().apply {
        name = numberChapter
        date_upload = dateFormat.tryParse(stringUpdateTime)
        url = "/truyen/$slug/chuong-$numberChapter"
    }
}

@Serializable
class ResultChapter(
    val chapters: List<ChapterDto>,
)

typealias ListManga = ResultDto<Result>

@Serializable
class Result(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: List<MangaDto>,
)

@Serializable
class MangaDto(
    private val id: String,
    private val name: String,
    private val photo: String,
    private val nameEn: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        thumbnail_url = baseUrl + photo
        url = "/truyen/$nameEn#$id"
    }
}

@Serializable
class ChapterWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ChapterBody,
)

typealias ChapterBody = ResultDto<ResultContent>

@Serializable
class ResultContent(
    val data: List<String>,
)
