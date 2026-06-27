package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class TermsResult(
    val terms: List<Term>,
)

@Serializable
class Term(
    val slug: String,
)

@Serializable
class TaxonomyMangas(
    @SerialName("manga_list") val mangaList: List<MangaItem>,
    val pagination: Pagination,
)

@Serializable
class Pagination(
    val page: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class MangaItem(
    private val title: String,
    val slug: String,
    private val description: String?,
    private val author: String?,
    private val status: String,
    private val type: String,
    val chapters: List<Chapter>,
    @SerialName("created_at") private val createdAt: String,
    @SerialName("alt_titles") private val altTitles: String?,
    @SerialName("term_list") private val termList: String? = null,
    @SerialName("cover_url") private val coverUrl: String,
) {
    fun isCompleted(): Boolean = status.equals("completed", true) || status.equals("finished", true)

    fun toSManga(): SManga = SManga.create().apply {
        url = "/manga/$slug/"
        title = this@MangaItem.title
        thumbnail_url = coverUrl
        author = this@MangaItem.author
        status = when (this@MangaItem.status.lowercase()) {
            "ongoing", "publishing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val termMap = mutableMapOf<String, MutableList<String>>()
        termList?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 3) {
                termMap.getOrPut(parts[1]) { mutableListOf() }.add(parts[0])
            }
        }

        description = buildString {
            this@MangaItem.description?.cleanHtml()?.let {
                append(it.cleanHtml()) // clean raw escape then remove tags
                append("\n\n")
            }
            append("Tipe: ${this@MangaItem.type.replaceFirstChar { it.uppercase() }}\n")
            termMap["group"]?.let { append("Group: ${it.joinToString()}\n") }
            termMap["character"]?.let { append("Karakter: ${it.joinToString()}\n") }
            termMap["series"]?.let { append("Seri: ${it.joinToString()}\n") }
            this@MangaItem.altTitles?.takeIf { it.isNotBlank() }?.let {
                append("Judul Alternatif: $it\n")
            }
        }.trim()

        genre = termMap["genre"]?.joinToString()
    }

    private fun String.cleanHtml(): String = Jsoup.parseBodyFragment(this).text()
}

@Serializable
class Chapter(
    private val id: String,
    @SerialName("chapter_number") private val chapterNumber: Float,
    @SerialName("created_at") private val createdAt: String,
    private val title: String? = null,
) {
    fun toSChapter(isLast: Boolean = false): SChapter = SChapter.create().apply {
        url = id
        name = "Chapter ${chapterNumber.format()}${if (isLast) " END" else ""}"
        chapter_number = chapterNumber
        date_upload = dateFormat.tryParse(createdAt)
    }

    private fun Float.format(): String = toString().removeSuffix(".0")
}

@Serializable
class PageList(
    @SerialName("content_urls") private val contentUrls: List<String>,
) {
    val pages: List<String>
        get() = contentUrls.map { page ->
            when {
                page.contains("/uploads/") && !page.contains("/storage/uploads/") -> page.replace("/uploads/", "/storage/uploads/")
                page.contains("/upload/") && !page.contains("/storage/upload/") -> page.replace("/upload/", "/storage/upload/")
                else -> page
            }
        }
}

@Serializable
class EncryptedDto(
    @SerialName("_enc_resp_") val encrypted: String,
)
