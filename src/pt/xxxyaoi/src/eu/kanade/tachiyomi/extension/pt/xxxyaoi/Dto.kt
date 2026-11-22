package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import keiyoushi.utils.firstInstance
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.jsoup.nodes.Document
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

// The library grid's data-x_ config, in the order the site's fetchLibrary reads it
class LibraryConfig(values: JsonArray) {
    private val tokenField = values[0].string
    private val token = values[1].string
    private val checksumField = values[2].string
    private val pageFactor = values[3].int
    private val searchFactor = values[4].int
    private val statusFactor = values[5].int
    private val genreFactor = values[6].int
    private val checksumBase = values[7].int
    val cookie = values[8].string to values[9].string

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

// A chapter row's data-x_ value: id, slug, name
class ChapterRow(values: JsonArray) {
    val slug = values[1].string
    val name = values[2].string
}

@Serializable
class Genre(val name: String, val slug: String)

class ReaderData(
    private val key: String,
    private val payload: List<String>,
    private val offset: Int,
    private val shift: Int,
    private val mods: List<Int>,
) {
    // Port of the site reader's inline decoder; entries before the offset are decoys
    fun toPageUrls() = payload.drop(offset).map { entry ->
        val reversed = Base64.decode(entry, Base64.DEFAULT).reversedArray()
        val bytes = ByteArray(reversed.size) { i ->
            ((reversed[i] - shift - mods.sumOf { i % it }) xor key[i % key.length].code).toByte()
        }
        try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    companion object {
        // Block keys, ids and the decoder's variable names are renamed per page: values by type, the byte transform from the decoder
        fun from(document: Document): ReaderData {
            val values = document.selectFirst("script[type=application/json]")!!.data().parseAs<JsonObject>().values
            val html = document.html()
            // `(_raw.charCodeAt(i) - <shift> - (i % N)) & 255`
            val transform = TRANSFORM_REGEX.find(html)!!.groupValues[1]
            val shift = TERM_REGEX.findAll(MOD_REGEX.replace(transform, "")).sumOf { term ->
                val token = term.groupValues[1]
                token.toIntOrNull() ?: Regex("""var\s+$token\s*=\s*(\d+)""").find(html)!!.groupValues[1].toInt()
            }
            return ReaderData(
                key = values.firstNotNullOf { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content },
                payload = values.firstInstance<JsonArray>().map { it.string },
                offset = values.firstNotNullOf { (it as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.intOrNull },
                shift = shift,
                mods = MOD_REGEX.findAll(transform).map { it.groupValues[1].toInt() }.toList(),
            )
        }

        private val TRANSFORM_REGEX = Regex("""charCodeAt\(\s*i\s*\)(.*?)\)\s*&\s*255""")
        private val MOD_REGEX = Regex("""\(\s*i\s*%\s*(\d+)\s*\)""")
        private val TERM_REGEX = Regex("""-\s*(\w+)""")
    }
}

// The site's atob(value).split('').reverse()
fun String.decodeReversedBase64() = Base64.decode(this, Base64.DEFAULT).reversedArray().decodeToString()
