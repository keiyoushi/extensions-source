package eu.kanade.tachiyomi.extension.ja.rawdevartart

import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.ChapterDetailsDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.ChapterDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaDetailsDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.MangaDto
import eu.kanade.tachiyomi.extension.ja.rawdevartart.dto.PaginatedMangaList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.Jsoup
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Locale

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

fun PaginatedMangaList.toMangasPage(): MangasPage {
    val manga = mangaList.map { it.toSManga() }
    val hasNextPage = (pagi.button?.next ?: 0) != 0

    return MangasPage(manga, hasNextPage)
}

private fun MangaDto.toSManga() = SManga.create().apply {
    // The website URL is manually calculated using a slugify function that I am too
    // lazy to reimplement.
    url = "/spa/manga/$id"
    title = name
    thumbnail_url = coverImage
}

fun MangaDetailsDto.toSManga() = SManga.create().apply {
    title = detail.name
    author = authors.joinToString { it.name }
    description = buildString {
        if (!detail.alternativeName.isNullOrEmpty()) {
            append("Alternative Title: ")
            appendLine(detail.alternativeName)
            appendLine()
        }

        if (!detail.description.isNullOrEmpty()) {
            append(detail.description)
        }
    }
    genre = tags.joinToString { it.name }
    status = if (detail.status) SManga.COMPLETED else SManga.ONGOING
    thumbnail_url = detail.coverImageFull ?: detail.coverImage
}

fun MangaDetailsDto.toSChapterList() = chapters.map { it.toSChapter(detail.id) }

private fun ChapterDto.toSChapter(mangaId: Int) = SChapter.create().apply {
    url = "/spa/manga/$mangaId/$number"
    name = buildString {
        append("Chapter ")
        append(formatChapterNumber(number))

        if (title.isNotEmpty()) {
            append(": ")
            append(title)
        }
    }
    chapter_number = number
    date_upload = runCatching {
        dateFormat.parse(datePublished)!!.time
    }.getOrDefault(0L)
}

fun ChapterDetailsDto.toPageList(baseUrl: String): List<Page> {
    val document = Jsoup.parseBodyFragment(detail.content!!, baseUrl)

    return document.select("div.chapter-img canvas").mapIndexed { i, it ->
        Page(i, imageUrl = it.absUrl("data-srcset"))
    }
}

private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols().apply { decimalSeparator = '.' },
)

fun formatChapterNumber(chapterNumber: Float): String {
    return formatter.format(chapterNumber)
}
