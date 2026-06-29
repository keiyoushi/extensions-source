package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class MangttoResponse<T>(
    val success: Boolean,
    val data: T,
)

@Serializable
class MangttoTrendsData(
    val mangas: List<MangttoManga> = emptyList(),
)

@Serializable
class MangttoLatestData(
    val chapters: List<MangttoLatestChapter> = emptyList(),
    val pages: Int = 1,
)

@Serializable
class MangttoSearchData(
    val hits: List<MangttoManga> = emptyList(),
    val estimatedTotalHits: Int = 0,
    val limit: Int = 42,
)

@Serializable
class MangttoManga(
    private val title: String,
    private val slug: String,
    private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangttoManga.title
        url = slug
        thumbnail_url = coverImage
    }
}

@Serializable
class MangttoLatestChapter(
    private val title: String,
    val slug: String,
    private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangttoLatestChapter.title
        url = slug
        thumbnail_url = coverImage
    }
}

@Serializable
class MangttoDetailData(
    private val slug: String,
    private val title: String,
    private val status: String,
    private val description: String? = null,
    private val coverImage: String? = null,
    private val genres: List<MangttoGenre> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangttoDetailData.title
        url = slug
        thumbnail_url = coverImage
        description = this@MangttoDetailData.description
        status = when (this@MangttoDetailData.status) {
            "FINISHED" -> SManga.COMPLETED
            "ONGOING" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString { it.name }
    }
}

@Serializable
class MangttoGenre(val name: String)

@Serializable
class MangttoChapterPageData(
    val chapters: List<MangttoChapter> = emptyList(),
    val pages: Int = 1,
)

@Serializable
class MangttoChapter(
    private val chapter: Float,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterStr = chapter.toString().removeSuffix(".0")
        name = "Bölüm $chapterStr"
        url = "$mangaSlug/$chapterStr"
    }
}

@Serializable
class MangttoPageData(
    val chapter: MangttoChapterDetail,
)

@Serializable
class MangttoChapterDetail(
    val chapter: Float,
    val static: List<MangttoStatic> = emptyList(),
)

@Serializable
class MangttoStatic(
    val id: String,
    val fansubId: String,
    val fileSize: Int = 0,
)
