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

    fun List<String>?.orUnknown(): String =
        this
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.equals("N/A", true) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString()
            ?: "Tidak Diketahui"

    fun toSManga(): SManga = SManga.create().apply {
        val termMap = mutableMapOf<String, MutableList<String>>()
        termList?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 3) {
                termMap.getOrPut(parts[1]) { mutableListOf() }.add(parts[0])
            }
        }

        val mangaAuthor = this@MangaItem.author?.takeIf { it.isNotBlank() }
            ?: listOf("author", "artist", "author_artist", "creator")
                .firstNotNullOfOrNull { key ->
                    termMap[key]?.takeIf { it.isNotEmpty() }?.joinToString()
                }

        url = "/manga/$slug/"
        title = this@MangaItem.title
        thumbnail_url = coverUrl
        author = mangaAuthor

        status = when {
            this@MangaItem.status.lowercase() in listOf("ongoing", "publishing") -> SManga.ONGOING
            isCompleted() -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        description = buildString {
            val cleanDesc = this@MangaItem.description
                ?.takeIf { it.isNotBlank() }
                ?.let { desc ->
                    val document = Jsoup.parseBodyFragment(Parser.unescapeEntities(desc, false))

                    val targetNode = document.select("p").firstOrNull {
                        it.text().lowercase().removeSuffix(":").trim() != "sinopsis"
                    } ?: document.selectFirst("p") ?: document.body()

                    targetNode.select("*").removeIf {
                        val text = it.text().trim().lowercase()
                        text.startsWith("download batch") ||
                            text.startsWith("download volume")
                    }

                    targetNode.select("b, strong").forEach {
                        it.prepend("**")
                        it.append("**")
                    }
                    targetNode.select("br").prepend("\\n")

                    targetNode.text().replace("\\n", "\n").trim()
                }
                ?.takeIf { it.isNotBlank() }

            val isManhwa =
                this@MangaItem.type.equals("manhwa", ignoreCase = true) ||
                    termMap["series"]?.any { it.equals("Manhwa", ignoreCase = true) } == true

            if (cleanDesc != null) {
                val descTitle = if (isManhwa) "Sinopsis" else "Daftar Chapter"
                append("\n\n**$descTitle:**\n")
                append(cleanDesc)
            } else {
                append("\n\nTidak ada deskripsi yang tersedia bosque")
            }

            append("\n\n")

            if (!isManhwa) {
                append("**Tipe:** ${this@MangaItem.type.replaceFirstChar { it.uppercase() }}\n")
                append("**Group:** ${termMap["group"].orUnknown()}\n")
                append("**Karakter:** ${termMap["character"].orUnknown()}\n")
            }

            termMap["series"]?.let {
                append("**Seri:** ${it.joinToString()}\n")
            }

            this@MangaItem.altTitles?.takeIf { it.isNotBlank() }?.let { alt ->
                val formattedAlts = alt.split("|", ",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .joinToString()

                append("\n\n**Judul Alternatif:**\n$formattedAlts")
            }
        }.trim()

        genre = termMap["genre"]
            ?.sortedBy { it.lowercase() }
            ?.joinToString {
                it.lowercase().replaceFirstChar { c -> c.uppercase() }
            }
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
