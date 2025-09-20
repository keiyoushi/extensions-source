package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Base64
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
    fun getUrls(key: String): List<String> {
        try {
            val decrypted = this.decryptImages(imageUrls, key)
            return Json.decodeFromString<List<String>>(decrypted)
        } catch (error: Exception) {
            Log.e("SakuraMangas", "Failed to decrypt images", error)
            throw Exception("Failed to decrypt images")
        }
    }

    /**
     * Decrypts the content using XOR with a specific key.
     * Equivalent to the JavaScript decryptImages method.
     *
     * ```javascript
     * function decryptImages(contentBase64, key) {
     *   const decoded = atob(contentBase64);
     *   let keyAccumulator = 0;
     *   for (let i = 0; i < key.length; i++) {
     *     keyAccumulator += key.charCodeAt(i) * (i + 1);
     *   }
     *   let transformedKey = "";
     *   for (let j = 0; j < key.length; j++) {
     *     const mask = ((keyAccumulator >> 8) & 0xff) >>> 0;
     *     const transformedCharCode = key.charCodeAt(j) ^ mask ^ (j % 256);
     *     transformedKey += String.fromCharCode(transformedCharCode);
     *   }
     *   const transformedLen = transformedKey.length;
     *   let output = "";
     *   let prevInput = 0;
     *   for (let i = 0; i < decoded.length; i++) {
     *     const kByte = transformedKey.charCodeAt(i % transformedLen) ^ prevInput;
     *     const inputByte = decoded.charCodeAt(i);
     *     const outByte = inputByte ^ kByte;
     *     output += String.fromCharCode(outByte);
     *     prevInput = inputByte;
     *   }
     *   return output;
     * }
     * ```
     */
    private fun decryptImages(content: String, key: String): String {
        // Step 1: base64 decode to raw bytes (equivalent to atob -> Latin1 bytes)
        val decodedBytes = Base64.decode(content, Base64.DEFAULT)

        // Step 2: accumulate a rolling sum based on the key and index (i + 1)
        var acc = 0
        for (i in key.indices) {
            acc += key[i].code * (i + 1)
        }

        // Step 3: transform the key into an intermediate key using acc and index
        val transformedKey = CharArray(key.length)
        for (j in key.indices) {
            val keyCode = key[j].code
            val mask = (acc ushr 8) and 0xFF
            val transformed = keyCode xor mask xor (j % 256)
            transformedKey[j] = transformed.toChar()
        }

        // Step 4: stream-decrypt using XOR with previous input byte state
        val transformedLen = transformedKey.size
        val output = StringBuilder(decodedBytes.size)
        var prevInput = 0
        for (i in decodedBytes.indices) {
            val k = transformedKey[i % transformedLen].code xor prevInput
            val inputByte = decodedBytes[i].toInt() and 0xFF
            val outByte = inputByte xor k
            output.append(outByte.toChar())
            prevInput = inputByte
        }

        return output.toString()
    }
}
