package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.sha256
import java.io.IOException
import kotlin.time.Clock

// The reader build's 64-hex secret; its header names, heartbeat path and loader field are slices of it
internal class ReaderSecret private constructor(secret: String) {
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

        private val STRING_TABLE_REGEX = Regex("""function ([\w$]+)\(\)\{const ([\w$]+)=\[([^\]]*)\]; ?return \1=function\(\)\{return \2\}""")
        private val STRING_REGEX = Regex(""""([^"]*)"""")
        private val CONCAT_REGEX = Regex("""(?<![\w$.])[\w$]+=((?:[\w$]+\(\d+\)|"[0-9a-f]*")(?:\+(?:[\w$]+\(\d+\)|"[0-9a-f]*"))+)[,;]""")
        private val PART_REGEX = Regex("""([\w$]+)\((\d+)\)|"([0-9a-f]*)"""")
        private val SECRET_REGEX = Regex("""[0-9a-f]{64}""")

        // The one 64-hex join of string-table lookups and hex literals, over every rotation of the obfuscator's table
        fun fromBundle(bundle: String): ReaderSecret {
            val table = STRING_TABLE_REGEX.find(bundle)?.groupValues?.get(3)
                ?.let { STRING_REGEX.findAll(it).map { entry -> entry.groupValues[1] }.toList() }
                ?.takeIf { it.isNotEmpty() }
                ?: throw ReaderChangedException()
            val secret = CONCAT_REGEX.findAll(bundle)
                .map { PART_REGEX.findAll(it.groupValues[1]).toList() }
                .filter { parts -> parts.any { it.groups[3] != null } }
                .flatMap { parts ->
                    table.indices.asSequence().mapNotNull { rotation ->
                        parts.joinToString("") { part ->
                            part.groups[2]?.let { decodeEntry(table[(it.value.toInt() + rotation) % table.size]) } ?: part.groupValues[3]
                        }.takeIf(SECRET_REGEX::matches)
                    }
                }
                .distinct()
                .toList()
                .singleOrNull()
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

internal class ReaderChangedException : IOException("The site's reader changed, update the extension")
