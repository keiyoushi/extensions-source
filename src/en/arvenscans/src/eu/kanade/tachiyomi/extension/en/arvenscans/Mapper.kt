package eu.kanade.tachiyomi.extension.en.arvenscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

fun PostSummaryDto.toSMangaSummary(): SManga {
    val cleanTitle = postTitle.trim()
    if (cleanTitle.isEmpty()) throw Exception(MISSING_TITLE_MESSAGE)

    return SManga.create().apply {
        url = "$slug#$id"
        title = cleanTitle
        thumbnail_url = featuredImage
        genre = genres.joinToString { it.name }
        status = mapStatus(seriesStatus)
    }
}

fun PostDto.toSMangaDetailsModel(): SManga {
    val cleanTitle = postTitle.trim()
    if (cleanTitle.isEmpty()) throw Exception(MISSING_TITLE_MESSAGE)

    return SManga.create().apply {
        url = "$slug#$id"
        title = cleanTitle
        description = buildDescription(this@toSMangaDetailsModel)
        author = this@toSMangaDetailsModel.author?.trim()?.takeUnless { it.isEmpty() }
        artist = this@toSMangaDetailsModel.artist?.trim()?.takeUnless { it.isEmpty() }
        genre = buildGenre(seriesType, genres)
        status = mapStatus(seriesStatus)
        thumbnail_url = featuredImage
        initialized = true
    }
}

fun PostChapterDto.toSChapterModel(mangaSlug: String, dateFormat: SimpleDateFormat): SChapter {
    val rawNumber = number.toString().trim('"').trim()
    val fallbackNumber = slug.substringAfter("chapter-", "")
    val chapterNumberText = if (rawNumber.isNotEmpty()) rawNumber else fallbackNumber
    val chapterTitle = title?.trim().orEmpty()

    return SChapter.create().apply {
        url = "$mangaSlug/$slug#$id"
        name = buildString {
            if (isAccessible == false || isLocked == true) {
                append("🔒 ")
            }

            append("Chapter")
            if (chapterNumberText.isNotEmpty()) {
                append(' ')
                append(chapterNumberText)
            }

            if (chapterTitle.isNotEmpty()) {
                append(" - ")
                append(chapterTitle)
            }
        }
        chapterNumberText.toFloatOrNull()?.let { chapter_number = it }
        date_upload = dateFormat.tryParse(createdAt)
    }
}

private fun buildDescription(post: PostDto): String? {
    val synopsis = post.postContent
        ?.takeIf { it.isNotBlank() }
        ?.replace("<br>", "\n")
        ?.replace("<br/>", "\n")
        ?.replace("<br />", "\n")
        ?.let { Jsoup.parse(it).text() }
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val alternativeTitles = post.alternativeTitles
        ?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toList()
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString("\n")

    return buildString {
        synopsis?.let { append(it) }
        alternativeTitles?.let {
            if (isNotEmpty()) append("\n\n")
            append("Alternative titles:\n")
            append(it)
        }
    }.takeIf { it.isNotBlank() }
}

private fun buildGenre(seriesType: String?, genres: List<GenreDto>): String? {
    val values = mutableListOf<String>()

    when (seriesType?.uppercase()) {
        "MANGA" -> values += "Manga"
        "MANHUA" -> values += "Manhua"
        "MANHWA" -> values += "Manhwa"
    }

    genres.mapTo(values) { it.name }

    val out = values.distinct().joinToString()
    return out.takeIf { it.isNotBlank() }
}

private fun mapStatus(status: String?): Int = when (status) {
    "ONGOING", "COMING_SOON", "MASS_RELEASED" -> SManga.ONGOING
    "COMPLETED" -> SManga.COMPLETED
    "CANCELLED", "DROPPED" -> SManga.CANCELLED
    else -> SManga.UNKNOWN
}
