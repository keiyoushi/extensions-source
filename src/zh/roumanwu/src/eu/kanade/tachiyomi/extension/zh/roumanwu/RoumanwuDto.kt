package eu.kanade.tachiyomi.extension.zh.roumanwu

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

@Serializable
data class NextData<T>(val props: Props<T>)

@Serializable
data class Props<T>(val pageProps: T)

@Serializable
data class Book(
    val id: String,
    val name: String,
//  val alias: List<String>,
    val description: String,
    val coverUrl: String,
    val author: String,
    val continued: Boolean,
    val tags: List<String>,
    val updatedAt: String? = null,
    val activeResource: Resource? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/books/$id"
        title = name
        author = this@Book.author
        description = this@Book.description
        genre = tags.joinToString(", ")
        status = if (continued) SManga.ONGOING else SManga.COMPLETED
        thumbnail_url = coverUrl
    }

    /** 正序 */
    fun getChapterList() = activeResource!!.chapters.mapIndexed { i, it ->
        SChapter.create().apply {
            url = "/books/$id/$i"
            name = it
        }
    }.apply {
        if (!updatedAt.isNullOrBlank()) {
            this[lastIndex].date_upload = DATE_FORMAT.parse(updatedAt)?.time ?: 0L
        }
    }

    private val uuid by lazy { UUID.fromString(id) }
    override fun hashCode() = uuid.hashCode()
    override fun equals(other: Any?) = other is Book && uuid == other.uuid

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
    }
}

@Serializable
data class Resource(val chapters: List<String>)

@Serializable
data class BookList(val books: List<Book>, val hasNextPage: Boolean) {
    fun toMangasPage() = MangasPage(books.map(Book::toSManga), hasNextPage)
}

@Serializable
data class HomePage(
    val headline: Book,
    val best: List<Book>,
    val hottest: List<Book>,
    val daily: List<Book>,
    val recentUpdatedBooks: List<Book>,
    val endedBooks: List<Book>,
) {
    fun getPopular() = (listOf(headline) + best + hottest + daily + endedBooks).distinct()
}

fun List<Book>.toMangasPage() = MangasPage(this.map(Book::toSManga), false)

@Serializable
data class BookDetails(val book: Book)

@Serializable
data class Chapter(
    val statusCode: Int? = null,
    val images: List<Image>? = null,
    val chapterAPIPath: String? = null,
) {
    fun getPageList() = images!!.mapIndexed { i, it ->
        Page(i, imageUrl = it.src + if (it.scramble) ScrambledImageInterceptor.SCRAMBLED_SUFFIX else "")
    }
}

@Serializable
data class ChapterWrapper(val chapter: Chapter)

@Serializable
data class Image(val src: String, val scramble: Boolean)
