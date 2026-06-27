package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.SimpleDateFormat

val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

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
    val mangaList: List<MangaItem>,
    val pagination: Pagination,
)

@Serializable
class Pagination(
    val page: Int,
    val totalPages: Int,
)

@Serializable
class MangaItem(
    val title: String,
    val slug: String,
    val description: String?,
    val author: String?,
    val status: String,
    val type: String,
    val chapters: List<Chapter>,
    @SerialName("created_at") val createdAt: String,
    @SerialName("alt_titles") val altTitles: String?,
    @SerialName("term_list") val termList: String? = null,
    @SerialName("cover_url") val coverUrl: String,

) {
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

    fun String.cleanHtml(): String = Jsoup.parse(this).text().trim()
}

@Serializable
class Chapter(
    val id: String,
    @SerialName("chapter_number") val chapterNumber: Float,
    @SerialName("created_at") val createdAt: String,
    val title: String? = null,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = id
        name = "Chapter ${chapterNumber.format()}"
        chapter_number = chapterNumber
        date_upload = dateFormat.tryParse(createdAt)
    }

    private fun Float.format(): String = if (this % 1f == 0f) toInt().toString() else toString()
}

@Serializable
class PageList(
    @SerialName("content_urls") val contentUrls: List<String>,
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
