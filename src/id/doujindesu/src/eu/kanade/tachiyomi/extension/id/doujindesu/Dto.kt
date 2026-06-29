package eu.kanade.tachiyomi.extension.id.doujindesu

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
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
    fun isCompleted(): Boolean = status.lowercase() in listOf("completed", "finished")

    fun toSManga(): SManga = SManga.create().apply {
        url = "/manga/$slug/"
        title = this@MangaItem.title
        thumbnail_url = coverUrl
        author = this@MangaItem.author

        status = when {
            this@MangaItem.status.lowercase() in listOf("ongoing", "publishing") -> SManga.ONGOING
            isCompleted() -> SManga.COMPLETED
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
            append("**Tipe:** ${this@MangaItem.type.replaceFirstChar { it.uppercase() }}\n")

            termMap["group"]?.let { append("**Group:** ${it.joinToString()}\n") }
            termMap["character"]?.let { append("**Karakter:** ${it.joinToString()}\n") }
            termMap["series"]?.let { append("**Seri:** ${it.joinToString()}\n") }

            this@MangaItem.description?.takeIf { it.isNotBlank() }?.let { desc ->
                val unescapedDesc = Parser.unescapeEntities(desc, false)
                val document = Jsoup.parseBodyFragment(unescapedDesc)

                val paragraphs = document.select("p")
                val targetNode = paragraphs.firstOrNull {
                    it.text().lowercase().removeSuffix(":").trim() != "sinopsis"
                } ?: paragraphs.firstOrNull() ?: document.body()

                targetNode.select("b, strong").forEach {
                    it.prepend("**")
                    it.append("**")
                }
                targetNode.select("br").prepend("\\n")

                val cleanDesc = targetNode.text().replace("\\n", "\n")
                if (cleanDesc.isNotBlank()) append("\n\n$cleanDesc")
            }

            this@MangaItem.altTitles?.takeIf { it.isNotBlank() }?.let { alt ->
                val formattedAlts = alt.split("|", ",")
                    .filter { it.isNotBlank() }
                    .joinToString { it.trim() }
                append("\n\n**Judul Alternatif:**\n$formattedAlts")
            }
        }.trim()

        genre = termMap["genre"]?.joinToString()
    }
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
