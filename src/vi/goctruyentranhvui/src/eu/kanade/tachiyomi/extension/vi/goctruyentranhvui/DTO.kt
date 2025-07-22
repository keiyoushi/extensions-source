package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.US)

@Serializable
class ListChapter(
    val result: ResultChapter,
)

@Serializable
class ChapterDTO(
    private val numberChapter: String,
    private val stringUpdateTime: String,
) {
    fun toChapter(mangaUrl: String): SChapter = SChapter.create().apply {
        name = numberChapter
        date_upload = dateFormat.tryParse(stringUpdateTime)
        url = "$mangaUrl/chuong-$numberChapter}"
    }
}

@Serializable
class ResultChapter(
    val chapters: List<ChapterDTO>,
)

@Serializable
class ListManga(
    val result: Result,
)

@Serializable
class Result(
    val p: Int? = null,
    val next: Boolean? = null,
    val data: List<MangaDTO>,
)

@Serializable
class MangaDTO(
    private val name: String,
    private val photo: String,
    private val nameEn: String,
) {
    fun toManga(baseUrl: String): SManga = SManga.create().apply {
        title = name
        thumbnail_url = baseUrl + photo
        url = "$baseUrl/truyen/$nameEn"
    }
}

@Serializable
class SearchDTO(
    val result: List<MangaDTO>,
)

@Serializable
class ChapterWrapper(
    val headers: Map<String, String> = emptyMap(),
    val body: ChapterBody,
)

@Serializable
class ChapterBody(
    val result: ResultContent,
)

@Serializable
class ResultContent(
    val data: List<String>,
)
