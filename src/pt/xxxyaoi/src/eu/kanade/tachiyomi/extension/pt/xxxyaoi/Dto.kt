package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException

@Serializable
class LibraryResponse(val data: LibraryData)

@Serializable
class LibraryData(
    val html: String,
    @SerialName("current_page") val currentPage: Int,
    @SerialName("total_pages") val totalPages: Int,
)

@Serializable
class Genre(val name: String, val slug: String)

@Serializable
class ReaderData(
    private val auth: String,
    private val payload: List<String>,
    private val offset: Int,
) {
    // Port of the site reader's inline decoder; entries before the offset are decoys
    fun toPageUrls() = payload.drop(offset).map { entry ->
        val reversed = Base64.decode(entry, Base64.DEFAULT).reversedArray()
        val bytes = ByteArray(reversed.size) { (reversed[it].toInt() xor auth[it % auth.length].code).toByte() }
        try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }
}
