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

// The library grid's data-k: names of the token and checksum fields, the token, and the checksum's factors
@Serializable
class LibraryConfig(
    @SerialName("ft") private val tokenField: String,
    @SerialName("tk") private val token: String,
    @SerialName("fp") private val checksumField: String,
    @SerialName("mp") private val pageFactor: Int,
    @SerialName("ms") private val searchFactor: Int,
    @SerialName("mt") private val statusFactor: Int,
    @SerialName("mg") private val genreFactor: Int,
    @SerialName("mc") private val checksumBase: Int,
) {
    fun formFields(page: Int, search: String, status: String, genre: String) = listOf(
        tokenField to token,
        // The site counts the search in code points, the slugs in UTF-16 units
        checksumField to (
            page * pageFactor +
                search.codePointCount(0, search.length) * searchFactor +
                status.length * statusFactor +
                genre.length * genreFactor +
                checksumBase
            ).toString(),
    )
}

@Serializable
class ChapterRow(
    @SerialName("s") val slug: String,
    @SerialName("n") val name: String,
)

@Serializable
class Genre(val name: String, val slug: String)

class ReaderData(
    private val key: String,
    private val payload: List<String>,
    private val offset: Int,
    private val shift: Int,
) {
    // Port of the site reader's inline decoder; entries before the offset are decoys
    fun toPageUrls() = payload.drop(offset).map { entry ->
        val reversed = Base64.decode(entry, Base64.DEFAULT).reversedArray()
        val bytes = ByteArray(reversed.size) { ((reversed[it] - shift) xor key[it % key.length].code).toByte() }
        try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }
}

// The site's atob(value).split('').reverse()
fun String.decodeReversedBase64() = Base64.decode(this, Base64.DEFAULT).reversedArray().decodeToString()
