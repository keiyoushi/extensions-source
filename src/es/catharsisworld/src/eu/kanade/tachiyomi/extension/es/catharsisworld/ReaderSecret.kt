package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.util.Base64
import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.multisrc.catharsis.TilePlan
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonString
import java.io.IOException
import kotlin.time.Clock

// The reader build's 64-hex secret, whose slices name its headers, heartbeat path and loader field, and its page shuffle code
internal class ReaderSecret private constructor(secret: String, private val shuffleScript: String, private val shuffleFunction: String) {
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

    // The reader's 8x8 tile plan for a page, from its own code
    fun tilePlan(key: String, width: Int, height: Int): TilePlan {
        val call = "JSON.stringify($shuffleFunction(${key.toJsonString()},$width,$height,8,8))"
        val plan = QuickJs.create().use { it.evaluate(shuffleScript + call) as String }.parseAs<TilePlanDto>()
        return TilePlan(plan.cols.toIntArray(), plan.rows.toIntArray(), plan.colOrder.toIntArray(), plan.rowOrder.toIntArray())
    }

    companion object {
        private const val IV_SIZE = 12

        private val STRING_TABLE_REGEX = Regex("""function ([\w$]+)\(\)\{const ([\w$]+)=\[([^\]]*)\]; ?return \1=function\(\)\{return \2\}""")
        private val STRING_REGEX = Regex(""""([^"]*)"""")
        private val CONCAT_REGEX = Regex("""(?<![\w$.])[\w$]+=((?:[\w$]+\(\d+\)|"[0-9a-f]*")(?:\+(?:[\w$]+\(\d+\)|"[0-9a-f]*"))+)[,;]""")
        private val PART_REGEX = Regex("""([\w$]+)\((\d+)\)|"([0-9a-f]*)"""")
        private val SECRET_REGEX = Regex("""[0-9a-f]{64}""")
        private val DECODER_REGEX = Regex("""function ([\w$]+)\(([\w$]+),[\w$]+\)\{\2=\2-""")
        private val ALIAS_REGEX = Regex("""(?:const|let|var|,)\s*([\w$]+)\s*=\s*([\w$]+)(?![\w$(])""")
        private val FUNCTION_NAME_REGEX = Regex("""^function ([\w$]+)\(([\w$]+)""")
        private val CALL_REGEX = Regex("""=\s*([\w$]+)\(\s*([\w$]+)\s*\)""")
        private val OFFSET_MAP_REGEX = Regex("""(?:const|let|var|,)\s*([\w$]+)\s*=\s*\{((?:\s*_0x[0-9a-f]+\s*:\s*-?\d+\s*,?)+)\}""")
        private val OFFSET_ENTRY_REGEX = Regex("""(_0x[0-9a-f]+)\s*:\s*(-?\d+)""")
        private val OFFSET_LOOKUP_REGEX = Regex("""([\w$]+)\.(_0x[0-9a-f]+)""")
        private val LOOKUP_REGEX = Regex("""(?<![\w$.])([\w$]+)\((-?\d+)\)""")

        // The one 64-hex join of string-table lookups and hex literals, over every rotation of the obfuscator's table;
        // the shuffle code is the plan function returning {cols, colPerm, slices} and its generator, with lookups inlined
        fun fromBundle(bundle: String): ReaderSecret {
            val table = STRING_TABLE_REGEX.find(bundle)?.groupValues?.get(3)
                ?.let { STRING_REGEX.findAll(it).map { entry -> entry.groupValues[1] }.toList() }
                ?.takeIf { it.isNotEmpty() }
                ?: throw ReaderChangedException()
            val (rotation, secret) = CONCAT_REGEX.findAll(bundle)
                .map { PART_REGEX.findAll(it.groupValues[1]).toList() }
                .filter { parts -> parts.any { it.groups[3] != null } }
                .flatMap { parts ->
                    table.indices.asSequence().mapNotNull { rotation ->
                        parts.joinToString("") { part ->
                            part.groups[2]?.let { decodeEntry(table[(it.value.toInt() + rotation) % table.size]) } ?: part.groupValues[3]
                        }.takeIf(SECRET_REGEX::matches)?.let { rotation to it }
                    }
                }
                .distinct()
                .toList()
                .singleOrNull()
                ?: throw ReaderChangedException()

            val decoder = DECODER_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val lookups = mutableSetOf(decoder)
            do {
                val added = ALIAS_REGEX.findAll(bundle).count { it.groupValues[2] in lookups && lookups.add(it.groupValues[1]) }
            } while (added > 0)

            val planIndex = bundle.indexOf("return{cols:").takeIf { it >= 0 } ?: throw ReaderChangedException()
            val plan = functionAt(bundle, bundle.lastIndexOf("function ", planIndex))
            val (planName, planKey) = FUNCTION_NAME_REGEX.find(plan)?.destructured ?: throw ReaderChangedException()
            val generatorName = CALL_REGEX.findAll(plan).firstOrNull { it.groupValues[2] == planKey }?.groupValues?.get(1)
                ?: throw ReaderChangedException()
            val generator = functionAt(bundle, bundle.indexOf("function $generatorName("))

            fun inline(code: String): String {
                val offsets = OFFSET_MAP_REGEX.findAll(code).associate { map ->
                    map.groupValues[1] to OFFSET_ENTRY_REGEX.findAll(map.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                }
                return code
                    .replace(OFFSET_LOOKUP_REGEX) { offsets[it.groupValues[1]]?.get(it.groupValues[2]) ?: it.value }
                    .replace(LOOKUP_REGEX) {
                        if (it.groupValues[1] !in lookups) return@replace it.value
                        decodeEntry(table[(it.groupValues[2].toInt() + rotation).mod(table.size)]).toJsonString()
                    }
            }
            // Leftover lookup aliases in the copied functions must still resolve
            val stubs = (lookups - planName - generatorName).joinToString("") { "var $it=function(){return\"\"};" }
            return ReaderSecret(secret, stubs + inline(generator) + "\n" + inline(plan) + "\n", planName)
        }

        // The function source starting at [start], up to its closing brace
        private fun functionAt(bundle: String, start: Int): String {
            if (start < 0) throw ReaderChangedException()
            var depth = 0
            for (i in bundle.indexOf('{', start) until bundle.length) {
                when (bundle[i]) {
                    '{' -> depth++
                    '}' -> if (--depth == 0) return bundle.substring(start, i + 1)
                }
            }
            throw ReaderChangedException()
        }

        // Base64 with the lowercase letters first
        private fun decodeEntry(entry: String): String {
            val standard = String(CharArray(entry.length) { entry[it].let { char -> if (char.isLowerCase()) char.uppercaseChar() else char.lowercaseChar() } })
            return Base64.decode(standard, Base64.NO_WRAP).decodeToString()
        }
    }
}

internal class ReaderChangedException : IOException("The site's reader changed, update the extension")
