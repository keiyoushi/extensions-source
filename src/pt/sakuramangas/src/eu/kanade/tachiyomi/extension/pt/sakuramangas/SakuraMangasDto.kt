package eu.kanade.tachiyomi.extension.pt.sakuramangas

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Serializable
data class SakuraMangasResultDto(
    val hasMore: Boolean,
    val html: String,
    val success: Boolean,
) {

    fun asJsoup(baseUri: String = ""): Document {
        return Jsoup.parseBodyFragment(this.html, baseUri)
    }
}

@Serializable
data class SakuraMangaInfoDto(
    val titulo: String,
    val autor: String?,
    val sinopse: String?,
    val tags: List<String>,
    val demografia: String?,
    val status: String,
    val ano: Int?,
    val classificacao: String?,
    val mangadex: String?,
    val views: Int?,
    val avaliacao: Double?,
    val num_avaliacoes: Int?,
    val total_favoritos: Int?,
    val relacionados_html: List<String>,
    val primeiro_capitulo: Int?,
    val ultimo_capitulo: Int?,
    val favorito: String?,
    val nota_usuario: String?,
    val status_usuario: String?,
    val ultimo_cap_lido: String?,
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
data class SakuraMangaChapterReadDto(
    val imageUrls: List<String>,
    val numPages: Int,
)
