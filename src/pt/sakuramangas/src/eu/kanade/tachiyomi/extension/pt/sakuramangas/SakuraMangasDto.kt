package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
class SakuraMangasResultDto(
    val hasMore: Boolean,
    private val html: String,
) {
    fun asJsoup(baseUri: String = ""): Document {
        return Jsoup.parseBodyFragment(this.html, baseUri)
    }
}

@Serializable
class SakuraLatestMangaDto(
    @SerialName("titulo")
    val title: String,
    @SerialName("url_manga")
    val url: String,
    @SerialName("thumb")
    val thumbnailPath: String,
) {
    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
        title = this@SakuraLatestMangaDto.title
        url = this@SakuraLatestMangaDto.url
        thumbnail_url = "$baseUrl$thumbnailPath"
    }
}

@Serializable
class SakuraChapterListResponseDto(
    val success: Boolean,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("total_groups")
    val totalGroups: Int,
    val data: List<SakuraChapterGroupDto>,
)

@Serializable
class SakuraChapterGroupDto(
    @SerialName("numero")
    val number: Float,
    @SerialName("data_timestamp")
    val dataTimestamp: Long,
    @SerialName("versoes")
    val versions: List<SakuraChapterVersionDto>,
)

@Serializable
class SakuraChapterVersionDto(
    val id: Int,
    @SerialName("titulo")
    val title: String?,
    val url: String,
    val timestamp: Long,
    val scans: List<SakuraScanDto>,
)

@Serializable
class SakuraScanDto(
    @SerialName("nome")
    val name: String,
)

fun SakuraChapterGroupDto.toSChapterList(): List<SChapter> {
    return versions.map { version ->
        SChapter.create().apply {
            val chapterNum = if (number % 1 == 0f) number.toInt().toString() else number.toString()
            name = buildString {
                append("Capítulo $chapterNum")
                version.title?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
            }
            chapter_number = number
            date_upload = version.timestamp * 1000 // Convert to milliseconds
            scanlator = version.scans.joinToString { it.name }
            url = version.url
        }
    }
}

@Serializable
class SakuraMangaInfoDto(
    @SerialName("titulo")
    private val title: String,
    @SerialName("autor")
    private val author: String?,
    @SerialName("sinopse")
    private val synopsis: String?,
    private val tags: List<String>,
    @SerialName("demografia")
    private val demography: String?,
    private val status: String,
    @SerialName("ano")
    private val year: Int?,
    @SerialName("classificacao")
    private val classification: String?,
    @SerialName("avaliacao")
    private val rating: Double?,
) {
    fun toSManga(mangaUrl: String): SManga = SManga.create().apply {
        title = this@SakuraMangaInfoDto.title
        author = this@SakuraMangaInfoDto.author
        genre = tags.joinToString()
        status = when (this@SakuraMangaInfoDto.status) {
            "concluído" -> SManga.COMPLETED
            "em andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = buildString {
            synopsis?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            year?.let { appendLine("Ano: $it") }
            demography?.takeIf { it.isNotBlank() }?.let { appendLine("Demografia: $it") }
            classification?.takeIf { it.isNotBlank() }?.let { appendLine("Classificação: $it") }
            rating?.let { appendLine("Avaliação: $it") }
        }.trim()
        thumbnail_url = "${mangaUrl.trimEnd('/')}/thumb_256.jpg"
        url = mangaUrl.toHttpUrl().encodedPath
        initialized = true
    }
}

@Serializable
class SakuraMangaChapterReadResponseDto(
    val data: SakuraMangaChapterDataDto,
)

@Serializable
class SakuraMangaChapterDataDto(
    val encryptedUrls: String? = null,
    val encryptedImageKey: String? = null,
    val encryptedEphemeralKey: EncryptedKeyDto? = null,
)

@Serializable
class EncryptedKeyDto(
    val cipher: String,
    val payload: String,
)
