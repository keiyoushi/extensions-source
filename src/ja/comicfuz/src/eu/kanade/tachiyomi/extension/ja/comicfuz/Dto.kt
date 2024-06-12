package eu.kanade.tachiyomi.extension.ja.comicfuz

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class DayOfWeekResponse(
    @ProtoNumber(1) val mangas: List<Manga>,
)

@Serializable
class SearchResponse(
    @ProtoNumber(2) val mangas: List<Manga>,
    @ProtoNumber(3) val books: List<Book>,
    @ProtoNumber(6) val pageCountOfMangas: Int = 0,
    @ProtoNumber(7) val pageCountOfBooks: Int = 0,
)

interface ComicFuzEntry {
    val id: Int
    val title: String
    val cover: String
    val description: String

    val urlPart: String
    fun toSManga(cdnUrl: String): SManga = SManga.create().apply {
        url = "/$urlPart/$id"
        title = this@ComicFuzEntry.title
        thumbnail_url = cdnUrl + cover
        description = this@ComicFuzEntry.description
    }
}

@Serializable
class Manga(
    @ProtoNumber(1) override val id: Int,
    @ProtoNumber(2) override val title: String,
    @ProtoNumber(4) override val cover: String,
    @ProtoNumber(14) override val description: String,
) : ComicFuzEntry {
    override val urlPart get() = "manga"
}

@Serializable
class Book(
    @ProtoNumber(2) override val id: Int,
    @ProtoNumber(1) override val title: String,
    @ProtoNumber(3) override val cover: String,
    @ProtoNumber(10) override val description: String,
) : ComicFuzEntry {
    override val urlPart get() = "book"
}

@Serializable
class MangaDetailsResponse(
    @ProtoNumber(2) private val manga: Manga,
    @ProtoNumber(3) val chapterGroups: List<ChapterGroup>,
    @ProtoNumber(4) private val authors: List<Author>,
    @ProtoNumber(7) private val tags: List<Name>,
) {
    fun toSManga(cdnUrl: String) = manga.toSManga(cdnUrl).apply {
        genre = tags.joinToString { it.name }
        author = authors.joinToString { it.author.name }
    }
}

@Serializable
class Author(
    @ProtoNumber(1) val author: Name,
)

@Serializable
class Name(
    @ProtoNumber(2) val name: String,
)

@Serializable
class ChapterGroup(
    @ProtoNumber(2) val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val title: String,
    @ProtoNumber(5) private val points: Point,
    @ProtoNumber(8) private val date: String = "",
) {
    fun toSChapter() = SChapter.create().apply {
        url = "/manga/viewer/$id"
        name = if (points.amount > 0) {
            "\uD83D\uDD12 $title" // lock emoji
        } else {
            title
        }
        date_upload = try {
            dateFormat.parse(date)!!.time
        } catch (_: ParseException) {
            0L
        }
    }
}

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)

@Serializable
class Point(
    @ProtoNumber(2) val amount: Int = 0,
)

@Serializable
class MangaViewerResponse(
    @ProtoNumber(3) val pages: List<ViewerPage>,
)

@Serializable
class ViewerPage(
    @ProtoNumber(1) val image: Image? = null,
)

@Serializable
class Image(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(3) val iv: String = "",
    @ProtoNumber(4) val encryptionKey: String = "",
    @ProtoNumber(7) val isExtraPage: Boolean = false,
)
