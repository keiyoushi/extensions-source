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

// The reader build's 64-hex secret, whose slices name its headers, heartbeat path and loader field, and its page shuffle code.
// The secret and the shuffle are both computed by evaluating the reader's own code (with its string decoder) in QuickJS, so a
// change to how either is expressed (an inline concat, a wrapper function, an int array, a DOM guard, reordered return keys)
// is absorbed without a code change; only a change to the few structural anchors below needs an update.
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

    // The reader's 8x8 tile plan for a page, from its own code; any failure there means the code changed
    fun tilePlan(key: String, width: Int, height: Int): TilePlan {
        val call = "JSON.stringify($shuffleFunction(${key.toJsonString()},$width,$height,8,8))"
        val plan = try {
            QuickJs.create().use { it.evaluate(shuffleScript + call) as String }.parseAs<TilePlanDto>()
        } catch (e: Exception) {
            throw ReaderChangedException(e)
        }
        return plan.toTilePlan()
    }

    companion object {
        private const val IV_SIZE = 12

        // The obfuscator's string table function, decoder, and its rotation IIFE, run as-is in QuickJS so every f(123) lookup resolves
        private val TABLE_REGEX = Regex("""function ([\w$]+)\(\)\{const [\w$]+=\[""")
        private val DECODER_REGEX = Regex("""function ([\w$]+)\(([\w$]+),[\w$]+\)\{\2=\2-""")
        private val ALIAS_REGEX = Regex("""(?:const|,)\s*([\w$]+)=(NAME)(?![\w$(])""")

        // The secret: the variable feeding the "x-<name>" headers, whatever expression defines it (inline concat, v_(), ...)
        private val SECRET_VAR_REGEX = Regex("""["']x-["']\s*\+\s*([\w$]+)[.\[]""")
        private val IDENTIFIER_REGEX = Regex("""[A-Za-z_$][\w$]*""")
        private val CALL_REGEX = Regex("""([\w$]+)\(""")
        private val SECRET_REGEX = Regex("""[0-9a-f]{64}""")

        // The shuffle: the function returning {..colPerm..slices..} (any key order), and its per-key generator
        private val PLAN_REGEX = Regex("""return\{[^}]*\bcolPerm:[^}]*\bslices:|return\{[^}]*\bslices:[^}]*\bcolPerm:""")
        private val FUNCTION_NAME_REGEX = Regex("""^function ([\w$]+)\(([\w$]+)""")
        private val GENERATOR_REGEX = Regex("""=\s*([\w$]+)\(\s*(KEY)\s*\)""")

        // Window/document exist only so a DOM-presence guard in the plan passes; the plan never uses them to compute
        private const val DOM_STUB = "var Ze,window={},document={createElement:function(){return{}}};"

        fun fromBundle(bundle: String): ReaderSecret {
            val preamble = decoderPreamble(bundle)
            val secret = evaluate(preamble + secretScript(bundle))
                .takeIf(SECRET_REGEX::matches) ?: throw ReaderChangedException()
            val (shuffleBody, planName) = shuffleScript(bundle)
            return ReaderSecret(secret, preamble + shuffleBody, planName)
        }

        // arrayFn + decoder + rotation IIFE + their module aliases + DOM stubs: a scope where every table lookup works
        private fun decoderPreamble(bundle: String): String {
            val tableName = TABLE_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val tableFn = functionAt(bundle, bundle.indexOf("function $tableName(){const"))
            val decoder = DECODER_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val decoderFn = functionAt(bundle, bundle.indexOf("function $decoder("))
            val iife = iifeAt(bundle, tableName)
            val aliases = Regex(ALIAS_REGEX.pattern.replace("NAME", Regex.escape(decoder))).findAll(bundle)
                .map { it.groupValues[1] }.toSet()
            val aliasDecls = (aliases + decoder).joinToString("") { "var $it=$decoder;" }
            return "$tableFn;$decoderFn;$iife;$aliasDecls$DOM_STUB"
        }

        // Evaluate String(<secret expression>): follows the assignment of the header variable, pulling any function it calls and arrays those use
        private fun secretScript(bundle: String): String {
            val secretVar = SECRET_VAR_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            var expression = assignmentOf(bundle, secretVar) ?: throw ReaderChangedException()
            while (expression.matches(Regex("""[\w$]+"""))) {
                expression = assignmentOf(bundle, expression)?.takeIf { it != expression } ?: break
            }
            val dependencies = StringBuilder()
            for (match in CALL_REGEX.findAll(expression)) {
                val name = match.groupValues[1]
                val start = bundle.indexOf("function $name(").takeIf { it >= 0 } ?: continue
                val source = functionAt(bundle, start)
                dependencies.append(source).append(';')
                for (id in IDENTIFIER_REGEX.findAll(source).map { it.value }.toSet()) {
                    arrayAt(bundle, id)?.let { dependencies.append(it) }
                }
            }
            return "$dependencies;var __s=String($expression);"
        }

        // Plan function (any return key order) + its generator, as a scope to call per page
        private fun shuffleScript(bundle: String): Pair<String, String> {
            val planMatch = PLAN_REGEX.find(bundle) ?: throw ReaderChangedException()
            val plan = functionAt(bundle, bundle.lastIndexOf("function ", planMatch.range.first))
            val (planName, planKey) = FUNCTION_NAME_REGEX.find(plan)?.destructured ?: throw ReaderChangedException()
            val generatorName = Regex(GENERATOR_REGEX.pattern.replace("KEY", Regex.escape(planKey)))
                .find(plan)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val generator = functionAt(bundle, bundle.indexOf("function $generatorName("))
            return "$generator;$plan;" to planName
        }

        private fun evaluate(script: String): String = try {
            QuickJs.create().use { it.evaluate(script + "__s") as String }
        } catch (e: Exception) {
            throw ReaderChangedException(e)
        }

        // The right-hand side of `name = <expr>`, up to the first top-level , or ; (quote and bracket aware)
        private fun assignmentOf(bundle: String, name: String): String? {
            val match = Regex("""(?<![\w$.])${Regex.escape(name)}\s*=\s*(?!=)""").find(bundle) ?: return null
            var depth = 0
            var i = match.range.last + 1
            var quote = ' '
            var inString = false
            val start = i
            while (i < bundle.length) {
                val c = bundle[i]
                when {
                    inString -> if (c == '\\') {
                        i++
                    } else if (c == quote) {
                        inString = false
                    }
                    c == '"' || c == '\'' -> {
                        inString = true
                        quote = c
                    }
                    c == '(' || c == '[' || c == '{' -> depth++
                    c == ')' || c == ']' || c == '}' -> depth--
                    (c == ',' || c == ';') && depth == 0 -> return bundle.substring(start, i).trim()
                }
                i++
            }
            return null
        }

        // A top-level `var/const/let name=[ ... ]` declaration (quote and bracket aware), or null
        private fun arrayAt(bundle: String, name: String): String? {
            val match = Regex("""(?:var|const|let)\s+${Regex.escape(name)}\s*=\s*\[""").find(bundle) ?: return null
            val open = match.range.last
            var depth = 0
            var i = open
            var quote = ' '
            var inString = false
            while (i < bundle.length) {
                val c = bundle[i]
                when {
                    inString -> if (c == '\\') {
                        i++
                    } else if (c == quote) {
                        inString = false
                    }
                    c == '"' || c == '\'' || c == '`' -> {
                        inString = true
                        quote = c
                    }
                    c == '[' -> depth++
                    c == ']' -> if (--depth == 0) return "var $name=" + bundle.substring(open, i + 1) + ";"
                }
                i++
            }
            return null
        }

        // The IIFE `(function(..){..})(tableName, <checksum>)` that rotates the table into place
        private fun iifeAt(bundle: String, tableName: String): String {
            val tag = ")($tableName,"
            val tagIndex = bundle.indexOf(tag).takeIf { it >= 0 } ?: throw ReaderChangedException()
            val close = bundle.indexOf(')', tagIndex + tag.length).takeIf { it >= 0 } ?: throw ReaderChangedException()
            val start = bundle.lastIndexOf("(function(", tagIndex).takeIf { it >= 0 } ?: throw ReaderChangedException()
            return bundle.substring(start, close + 1)
        }

        // The function source starting at [start], up to its closing brace (quote aware, since table strings hold braces)
        private fun functionAt(bundle: String, start: Int): String {
            if (start < 0) throw ReaderChangedException()
            var depth = 0
            var quote = ' '
            var inString = false
            var i = bundle.indexOf('{', start)
            while (i in bundle.indices) {
                val c = bundle[i]
                when {
                    inString -> when (c) {
                        '\\' -> i++
                        quote -> inString = false
                    }
                    c == '"' || c == '\'' || c == '`' -> {
                        inString = true
                        quote = c
                    }
                    c == '{' -> depth++
                    c == '}' -> if (--depth == 0) return bundle.substring(start, i + 1)
                }
                i++
            }
            throw ReaderChangedException()
        }
    }
}

internal class ReaderChangedException(cause: Throwable? = null) : IOException("The site's reader changed, update the extension", cause)
