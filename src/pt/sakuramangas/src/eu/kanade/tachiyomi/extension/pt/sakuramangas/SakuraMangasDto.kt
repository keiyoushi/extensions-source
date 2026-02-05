package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
class SakuraMangasResultDto(
    val hasMore: Boolean,
    private val html: String,
) {

    fun asJsoup(baseUri: String = ""): Document = Jsoup.parseBodyFragment(this.html, baseUri)
}

@Serializable
class SakuraMangaInfoDto(
    private val titulo: String,
    private val autor: String?,
    private val sinopse: String?,
    private val tags: List<String>,
    private val demografia: String?,
    private val status: String,
    private val ano: Int?,
    private val classificacao: String?,
    private val avaliacao: Double?,
) {
    fun toSManga(mangaUrl: String): SManga = SManga.create().apply {
        title = titulo
        author = autor
        genre = tags.joinToString()
        status = when (this@SakuraMangaInfoDto.status) {
            "concluído" -> SManga.COMPLETED
            "em andamento" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        description = buildString {
            sinopse?.takeIf { it.isNotBlank() }?.let {
                appendLine(it)
                appendLine()
            }
            ano?.let { appendLine("Ano: $it") }
            demografia?.takeIf { it.isNotBlank() }?.let { appendLine("Demografia: $it") }
            classificacao?.takeIf { it.isNotBlank() }?.let { appendLine("Classificação: $it") }
            avaliacao?.let { appendLine("Avaliação: $it") }
        }.trim()
        thumbnail_url = "${mangaUrl.trimEnd('/')}/thumb_256.jpg"
        url = mangaUrl.toHttpUrl().encodedPath
        initialized = true
    }
}

@Serializable
class SakuraMangaChapterReadDto(
    val imageUrls: String,
)
