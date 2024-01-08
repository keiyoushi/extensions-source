package eu.kanade.tachiyomi.extension.en.disasterscans

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class ApiSearchComic(
    val id: String,
    val ComicTitle: String,
    val CoverImage: String,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = ComicTitle
        thumbnail_url = "$cdnUrl$CoverImage#thumbnail"
        url = "/comics/$id-${ComicTitle.titleToSlug()}"
    }
}

@Serializable
data class ApiComic(
    val id: String,
    val ComicTitle: String,
    val Description: String,
    val CoverImage: String,
    val Status: String,
    val Genres: String,
    val Author: String,
    val Artist: String,
) {
    fun toSManga(json: Json, cdnUrl: String) = SManga.create().apply {
        title = ComicTitle
        thumbnail_url = "$cdnUrl$CoverImage#thumbnail"
        url = "/comics/$id-${ComicTitle.titleToSlug()}"
        description = Description
        author = Author
        artist = Artist
        genre = json.decodeFromString<List<String>>(Genres).joinToString()
        status = Status.parseStatus()
    }
}

@Serializable
data class ApiChapter(
    val chapterID: Int,
    val chapterNumber: String,
    val ChapterName: String,
    val chapterDate: String,
) {
    fun toSChapter(mangaUrl: String) = SChapter.create().apply {
        url = "$mangaUrl/$chapterID-chapter-$chapterNumber"
        chapter_number = chapterNumber.toFloat()
        name = "Chapter $chapterNumber"
        if (ChapterName.isNotEmpty()) {
            name += ": $ChapterName"
        }
        date_upload = chapterDate.parseDate()
    }
}

@Serializable
data class NextData<T>(
    val props: Props<T>,
) {
    @Serializable
    data class Props<T>(val pageProps: T)
}

@Serializable
data class ApiChapterPages(
    val chapter: ApiPages,
) {
    @Serializable
    data class ApiPages(val pages: String)
}

private fun String.titleToSlug() = this.trim()
    .lowercase()
    .replace(DisasterScans.titleSpecialCharactersRegex, "-")
    .replace(DisasterScans.trailingHyphenRegex, "")

private fun String.parseDate(): Long {
    return runCatching {
        DisasterScans.dateFormat.parse(this)!!.time
    }.getOrDefault(0L)
}

private fun String.parseStatus(): Int {
    return when {
        contains("ongoing", true) -> SManga.ONGOING
        contains("completed", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
