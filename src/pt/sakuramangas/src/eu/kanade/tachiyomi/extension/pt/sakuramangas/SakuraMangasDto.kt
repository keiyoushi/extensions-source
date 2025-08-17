package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Log
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
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
    private val imageUrls: String,
) {
    fun getUrls(): List<String> {
        try {
            val decrypted = this.decryptImages(imageUrls, "Ch4v3-SuP3r-S3cr3t4-P4r4-0-L3it0r-2023!")
            return Json.decodeFromString<List<String>>(decrypted)
        } catch (error: Exception) {
            Log.e("SakuraMangas", "Failed to decrypt images", error)
            // In case of decryption error, returns an empty list
            return emptyList()
        }
    }

    /**
     * Decrypts the content using XOR with a specific key.
     * Equivalent to the JavaScript decryptImages method.
     */
    private fun decryptImages(content: String, key: String): String {
        val decodedContent = String(android.util.Base64.decode(content, android.util.Base64.DEFAULT))
        val result = StringBuilder()

        for (i in decodedContent.indices) {
            val charCode = decodedContent[i].code xor key[i % key.length].code
            result.append(charCode.toChar())
        }

        return result.toString()
    }
}
