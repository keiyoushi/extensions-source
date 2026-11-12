package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.sha256
import java.io.IOException
import kotlin.time.Clock

// The reader build's 64-hex secret; its header names, heartbeat path and loader field are slices of it
class ReaderSecret(secret: String) {
    val loaderField = "_" + secret.substring(0, 7)
    val signatureHeader = "x-" + secret.substring(7, 15)
    val liveHeader = "x-" + secret.substring(15, 23)
    val clientHeader = "x-" + secret.substring(23, 31)
    val clientValue = secret.substring(31, 43)
    val heartbeatPath = secret.substring(43, 51)
    private val signingKey = "$secret:p".sha256()
    private val loaderKey = "$secret:h".sha256()

    // "<now>.<first 40 hex digits of HMAC-SHA256 over "<kind>.<payload>.<now>">"
    fun signature(kind: String, payload: String): String {
        val now = Clock.System.now().toEpochMilliseconds()
        return "$now." + "$kind.$payload.$now".hmacSha256(signingKey).toHexString().take(40)
    }

    // base64url of a 12-byte IV and the AES-GCM ciphertext
    fun decryptLoader(blob: String): String {
        val bytes = Base64.decode(blob, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        return bytes.copyOfRange(IV_SIZE, bytes.size).aesDecrypt(aesGcm(loaderKey, bytes.copyOf(IV_SIZE))).decodeToString()
    }

    companion object {
        private const val IV_SIZE = 12

        // The obfuscator's string table: function f(){const t=[ "..",.. ];return f=function(){return t}...}
        private val STRING_TABLE_REGEX = Regex("""function ([\w$]+)\(\)\{const ([\w$]+)=\[([^\]]*)\]; ?return \1=function\(\)\{return \2\}""")
        private val STRING_REGEX = Regex(""""([^"]*)"""")

        // Any "<name> = <concat>" where every operand is a table lookup f(123) or a lowercase-hex literal.
        // The secret is built this way in every build; the number of operands and the literal positions vary.
        private val CONCAT_REGEX = Regex("""(?<![\w$.])([\w$]+)=((?:[\w$]+\(\d+\)|"[0-9a-f]*")(?:\+(?:[\w$]+\(\d+\)|"[0-9a-f]*"))+)[,;]""")
        private val PART_REGEX = Regex("""([\w$]+)\((\d+)\)|"([0-9a-f]*)"""")
        private val SECRET_REGEX = Regex("""[0-9a-f]{64}""")

        // Build-agnostic extraction. The obfuscator rotates its string table by an amount proven only by a
        // checksum we do not evaluate; the index offset inside the decoder is a constant we also fold in.
        // So for each candidate concat we try every table rotation and keep the join that is exactly 64 hex.
        // We never anchor on a specific concat shape or on the literal slice args (those differ per build and
        // may be obfuscated arithmetic): the only invariants used are the table-function shape, the decoder's
        // base64 (lowercase-first) alphabet, a concat of lookups + hex literals, the secret variable feeding
        // the "x-<name>" header derivations, and the result being 64 hex under exactly one rotation.
        fun fromBundle(bundle: String): ReaderSecret {
            val table = STRING_TABLE_REGEX.find(bundle)?.groupValues?.get(3)
                ?.let { STRING_REGEX.findAll(it).map { entry -> entry.groupValues[1] }.toList() }
                ?.takeIf { it.isNotEmpty() }
                ?: throw ReaderChangedException()

            val candidates = CONCAT_REGEX.findAll(bundle)
                .map { it.groupValues[1] to PART_REGEX.findAll(it.groupValues[2]).toList() }
                .filter { (_, parts) -> parts.any { it.groups[3] != null } } // must embed at least one hex literal
                .toList()

            fun secretsOf(parts: List<MatchResult>): List<String> = table.indices.mapNotNull { rotation ->
                parts.joinToString("") { part ->
                    part.groups[2]?.let { decodeEntry(table[(it.value.toInt() + rotation) % table.size]) }
                        ?: part.groupValues[3]
                }.takeIf(SECRET_REGEX::matches)
            }

            // Prefer the variable whose name feeds the "x-<name>" header derivations; fall back to all candidates.
            val preferred = candidates.filter { (name, _) -> bundle.contains("\"x-\"+$name") }
            val secret = (preferred.ifEmpty { candidates })
                .flatMap { (_, parts) -> secretsOf(parts) }
                .distinct().singleOrNull()
                ?: throw ReaderChangedException()
            return ReaderSecret(secret)
        }

        // Base64 with the lowercase letters first
        private fun decodeEntry(entry: String): String {
            val standard = String(CharArray(entry.length) { entry[it].let { char -> if (char.isLowerCase()) char.uppercaseChar() else char.lowercaseChar() } })
            return Base64.decode(standard, Base64.NO_WRAP).decodeToString()
        }
    }
}

class ReaderChangedException : IOException("The site's reader changed, update the extension")
