package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.util.Base64
import keiyoushi.utils.array
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

// The library grid's encoded config, in the order the site's fetchLibrary reads it
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

// A chapter row's encoded value: id, slug, name
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
    private val mask: Int,
) {
    // Port of the site reader's inline decoder; entries before the offset are decoys
    fun toPageUrls() = payload.drop(offset).map { entry ->
        val reversed = Base64.decode(entry, Base64.DEFAULT).reversedArray()
        val bytes = ByteArray(reversed.size) { i ->
            ((reversed[i] - shift - mods.sumOf { i % it }) xor key[i % key.length].code xor mask).toByte()
        }
        try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    companion object {
        // Field names and the byte transform as the page's decoder reads them
        fun from(document: Document): ReaderData {
            val html = document.html()
            fun constant(token: String) = token.toIntOrNull()
                ?: VARIABLE_REGEX.findAll(html).first { it.groupValues[1] == token }.groupValues[2].toInt()
            // `_b ^ _key.charCodeAt(i % _key.length) ^ <mask>`, with `var _key = _dat["<field>"]`
            val keyUse = KEY_REGEX.find(html)!!
            val keyField = FIELD_REGEX.findAll(html).first { it.groupValues[1] == keyUse.groupValues[1] }.groupValues[2]
            // `_dat["<payload>"].slice(_dat["<offset>"])`
            val (payloadField, offsetField) = SLICE_REGEX.find(html)!!.destructured
            // The data block: a JSON object, as is or as reversed base64
            val data = document.select(".reading-content script:not([src])").firstNotNullOf { script ->
                val text = script.data().trim()
                val json = text.takeIf { it.startsWith("{") } ?: text.reversed().decodeBase64OrNull()?.decodeToString()
                json?.takeIf { it.startsWith("{") }?.parseAs<JsonObject>()?.takeIf { payloadField in it }
            }
            // `(_raw.charCodeAt(i) - <shift> - (i % N)) & 255`, or `(_raw.codePointAt(i) + 256 - <shift> - (i % N)) % 256`
            val transform = TRANSFORM_REGEX.find(html)!!.groupValues[1]
            val shift = TERM_REGEX.findAll(MOD_REGEX.replace(transform, "")).sumOf { constant(it.groupValues[1]) }
            val mask = XOR_TERM_REGEX.findAll(keyUse.groupValues[2]).fold(0) { acc, term -> acc xor constant(term.groupValues[1]) }
            return ReaderData(
                key = data.getValue(keyField).string,
                payload = data.getValue(payloadField).array.map { it.string },
                offset = data.getValue(offsetField).int,
                shift = shift,
                mods = MOD_REGEX.findAll(transform).map { it.groupValues[1].toInt() }.toList(),
                mask = mask,
            )
        }

        private val TRANSFORM_REGEX = Regex("""(?:charCodeAt|codePointAt)\(\s*i\s*\)(.*?)\)\s*(?:&\s*255|%\s*256)""")
        private val MOD_REGEX = Regex("""\(\s*i\s*%\s*(\d+)\s*\)""")
        private val TERM_REGEX = Regex("""-\s*(\w+)""")
        private val KEY_REGEX = Regex("""charCodeAt\(\s*i\s*%\s*(\w+)\.length\s*\)((?:\s*\^\s*\w+)*)""")
        private val FIELD_REGEX = Regex("""var\s+(\w+)\s*=\s*\w+\[\s*["'](\w+)["']\s*]""")
        private val SLICE_REGEX = Regex("""\w+\[\s*["'](\w+)["']\s*]\.slice\(\s*\w+\[\s*["'](\w+)["']\s*]\s*\)""")
        private val XOR_TERM_REGEX = Regex("""\^\s*(\w+)""")
        private val VARIABLE_REGEX = Regex("""var\s+(\w+)\s*=\s*(\d+)""")
    }
}

private fun String.decodeBase64OrNull() = takeIf(BASE64_REGEX::matches)?.let { Base64.decode(it, Base64.DEFAULT) }

// The element's encoded data-* attribute, recognized by its value: a JSON array once decoded.
// The site's atob(value).split('').reverse(), after the version prefix newer values carry (`xy2$`)
fun Element.encodedData() = attributes().firstNotNullOf { attribute ->
    attribute.value.takeIf { attribute.key.startsWith("data-") }?.substringAfter('$')
        ?.decodeBase64OrNull()?.reversedArray()?.decodeToString()?.takeIf { it.startsWith("[") }
}

// Whole quantums, then a tail of 2 or 3 characters, padded or not
private val BASE64_REGEX = Regex("""(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}(?:==)?|[A-Za-z0-9+/]{3}=?)?""")
