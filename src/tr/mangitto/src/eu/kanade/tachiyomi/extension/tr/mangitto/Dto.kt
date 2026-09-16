package eu.kanade.tachiyomi.extension.tr.mangitto

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
class MangttoPopularData(
    val mangas: List<MangttoManga>,
    val total: Int,
)

@Serializable
class MangttoLatestData(
    val chapters: List<MangttoLatestChapter>,
    val total: Int,
)

@Serializable
class MangttoLatestChapter(
    val manga: MangttoManga,
)

@Serializable
class MangttoSearchData(
    val hits: List<MangttoSearchHit>,
    val estimatedTotalHits: Int,
)

@Serializable
class MangttoSearchHit(
    val document: MangttoManga,
)

@Serializable
class MangttoManga(
    private val title: String,
    val slug: String,
    private val coverImage: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangttoManga.title
        url = slug
        thumbnail_url = coverImage
    }
}

@Serializable
class MangttoGenres(
    val genres: List<String>,
)

@Serializable
class MangttoDetailData(
    private val slug: String,
    private val title: String,
    private val status: String,
    private val description: String? = null,
    private val coverImage: String? = null,
    private val genres: List<MangttoGenre>,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangttoDetailData.title
        url = slug
        thumbnail_url = coverImage
        description = this@MangttoDetailData.description
        status = when (this@MangttoDetailData.status) {
            "FINISHED" -> SManga.COMPLETED
            "RELEASING" -> SManga.ONGOING
            "HIATUS" -> SManga.ON_HIATUS
            "CANCELLED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        genre = genres.joinToString { it.name }
    }
}

@Serializable
class MangttoGenre(val name: String)

@Serializable
class MangttoChapterPageData(
    val chapters: List<MangttoChapter>,
    val total: Int,
)

@Serializable
class MangttoChapter(
    private val chapter: Float,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        val chapterStr = chapter.toString().removeSuffix(".0")
        name = "Bölüm $chapterStr"
        chapter_number = chapter
        url = chapterStr
        memo = buildJsonObject {
            put("mangaSlug", mangaSlug)
        }
    }
}

@Serializable
class MangttoPageData(
    val cdn: String,
    val uploads: List<MangttoUpload>,
)

@Serializable
class MangttoUpload(
    val fansubId: String,
    val fileLength: Int,
)
