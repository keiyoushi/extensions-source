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

    fun List<String>?.orUnknown(): String = this
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.equals("N/A", true) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString()
        ?: "Tidak Diketahui"

    fun List<String>?.orNull(): String? = this
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.equals("N/A", true) }
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString()

    fun toSManga(): SManga = SManga.create().apply {
        val termMap = mutableMapOf<String, MutableList<String>>()
        termList?.split("|")?.forEach {
            val parts = it.split(":")
            if (parts.size == 3) {
                termMap.getOrPut(parts[1]) { mutableListOf() }.add(parts[0])
            }
        }

        val mangaAuthor = this@MangaItem.author
            ?.takeIf { it.isNotBlank() && !it.equals("N/A", ignoreCase = true) }
            ?: listOf("author", "artist", "author_artist", "creator")
                .firstNotNullOfOrNull { key ->
                    termMap[key]
                        ?.orNull()
                }
            ?: termMap["group"].orNull()

        url = "/manga/$slug/"
        title = this@MangaItem.title
        thumbnail_url = coverUrl
        author = mangaAuthor

        status = when {
            this@MangaItem.status.lowercase() in listOf("ongoing", "publishing") -> SManga.ONGOING
            isCompleted() -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val finalDescription = buildString {
            val cleanDesc = this@MangaItem.description
                ?.takeIf { it.isNotBlank() }
                ?.let { desc ->
                    val document = Jsoup.parseBodyFragment(Parser.unescapeEntities(desc, false))
                    val root = document.selectFirst(".rich-text-content") ?: document.body()

                    val html = root.outerHtml()
                        .replace(textRegex, "%%BR%%")

                    val text = Jsoup.parse(html).text()

                    val lines = mutableListOf<String>()

                    for (line in text.split("%%BR%%")) {
                        var cleanLine = line
                            .replace(whitespaceRegex, " ")
                            .trim()

                        if (cleanLine.isBlank()) continue

                        val lower = cleanLine.lowercase()

                        val downloadIndex = listOf(
                            lower.indexOf("download batch"),
                            lower.indexOf("download volume"),
                        ).filter { it >= 0 }
                            .minOrNull()

                        if (downloadIndex != null) {
                            cleanLine = cleanLine.substring(0, downloadIndex).trim()
                        }

                        if (cleanLine.isNotBlank()) {
                            lines += cleanLine
                                .replaceFirst(synopsisRegex, "")
                                .trim()
                        }

                        if (downloadIndex != null) {
                            break
                        }
                    }

                    lines
                        .filter { it.isNotBlank() }
                        .takeIf { it.isNotEmpty() }
                }

            if (cleanDesc != null) {
                val isChapterList = cleanDesc.first().matches(chapterRegex)

                append("\n\n**${if (isChapterList) "Daftar Chapter" else "Sinopsis"}:**\n")
                append(
                    cleanDesc.joinToString(
                        if (isChapterList) "\n" else "\n\n",
                    ),
                )
            } else {
                append("\n\nTidak ada deskripsi yang tersedia bosque")
            }
            append("\n\n")

            val isManhwa =
                this@MangaItem.type.equals("manhwa", ignoreCase = true) ||
                    termMap["series"]?.any { it.equals("Manhwa", ignoreCase = true) } == true

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

                append("**Judul Alternatif:** $formattedAlts")
            }
        }.trim()

        description = finalDescription

        genre = termMap["genre"]
            ?.sortedBy { it.lowercase() }
            ?.joinToString { it.toTitleCase() }
    }
}

val synopsisRegex = Regex("""(?i)^\s*(?:sinopsis|synopsis)\s*:?\s*""")
val whitespaceRegex = Regex("\\s+")
val textRegex = Regex("(?i)<br\\s*/?>")
val chapterRegex = Regex("""^\d+(?:-\d+)?\.\s*.+$""")

private fun String.toTitleCase(): String = lowercase()
    .split(whitespaceRegex)
    .joinToString(" ") {
        it.replaceFirstChar { c -> c.uppercase() }
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
