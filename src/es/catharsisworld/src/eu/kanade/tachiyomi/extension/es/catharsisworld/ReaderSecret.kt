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

// The reader build's 64-hex secret and page shuffle, both computed by the reader's own code in QuickJS
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

    // The reader's 8x8 tile plan for a page
    fun tilePlan(key: String, width: Int, height: Int): TilePlan = evaluate(shuffleScript + "JSON.stringify($shuffleFunction(${key.toJsonString()},$width,$height,8,8))") {
        it.parseAs<TilePlanDto>()
    }.toTilePlan()

    companion object {
        private const val IV_SIZE = 12

        private val TABLE_REGEX = Regex("""function ([\w$]+)\(\)\{const [\w$]+=\[[\s\S]*?\];return \1=function""")
        private val DECODER_REGEX = Regex("""function ([\w$]+)\(([\w$]+),[\w$]+\)\{\2=\2-""")
        private val SECRET_VAR_REGEX = Regex("""["']x-["']\s*\+\s*([\w$]+)[.\[]""")
        private val PLAN_CALL_REGEX = Regex("""(?<![\w$.])[\w$]+=([\w$]+)\([^()]*,8,8\)""")
        private val SECRET_REGEX = Regex("""[0-9a-f]{64}""")

        private val MODULE_FUNCTION_REGEX = Regex("""(?<![\w$])function ([\w$]+)\(""")
        private val MODULE_DECLARATION_REGEX = Regex("""(?:(?<![\w$])(?:var|const|let)\s+|,)([\w$]+)\s*=(?![=>])""")
        private val ASSIGNMENT_REGEX = Regex("""(?<![\w$.])([\w$]+)\s*(?:[-+*/%&|^]|\*\*|<<|>>>?|&&|\|\||\?\?)?=(?![=>])|(?<![\w$.])([\w$]+)\s*(?:\+\+|--)|(?:\+\+|--)\s*([\w$]+)""")
        private val IDENTIFIER_REGEX = Regex("""(?<![\w$])[A-Za-z_$][\w$]*""")
        private val STRING_REGEX = Regex(""""(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`""")
        private val LOCAL_REGEX = Regex("""(?<![\w$])(?:var|const|let)\s+([\w$]+)|,\s*([\w$]+)\s*=(?![=>])|([\w$]+)\s*=>""")
        private val PARAMETERS_REGEX = Regex("""function\s*[\w$]*\s*\(([^)]*)\)|\(([^()]*)\)\s*=>|catch\s*\(([\w$]+)\)""")
        private val PARAMETER_NAME_REGEX = Regex("""^\s*([A-Za-z_$][\w$]*)""")

        // Pass the reader's presence check on window, document and document.createElement; any real use throws
        private const val DOM_STUB = "var window=new Proxy({},{get:function(){throw Error(\"dom\")},has:function(){throw Error(\"dom\")}})," +
            "document=new Proxy({},{get:function(t,p){if(p===\"createElement\")return function(){return window};throw Error(\"dom\")}," +
            "has:function(){throw Error(\"dom\")}});"

        fun fromBundle(bundle: String): ReaderSecret {
            val module = Module(bundle)
            val tableName = TABLE_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val decoder = DECODER_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val aliases = module.aliasesOf(decoder)
            val preamble = functionAt(bundle, bundle.indexOf("function $tableName(){const")) + ";" +
                functionAt(bundle, bundle.indexOf("function $decoder(")) + ";" +
                iifeAt(bundle, tableName) + ";" +
                aliases.joinToString("") { "var $it=$decoder;" } +
                DOM_STUB
            val provided = aliases + decoder + tableName + "window" + "document"

            val secretVar = SECRET_VAR_REGEX.find(bundle)?.groupValues?.get(1) ?: throw ReaderChangedException()
            val secret = evaluate(preamble + module.definitions(secretVar, provided) + "String($secretVar)") { it }
                .takeIf(SECRET_REGEX::matches) ?: throw ReaderChangedException()

            val planName = PLAN_CALL_REGEX.findAll(bundle).map { it.groupValues[1] }.distinct().singleOrNull()
                ?: throw ReaderChangedException()
            return ReaderSecret(secret, preamble + module.definitions(planName, provided), planName)
        }

        // Runs the reader's code; any failure there means the code changed
        private fun <T> evaluate(script: String, read: (String) -> T): T = try {
            read(QuickJs.create().use { it.evaluate(script) as String })
        } catch (e: Exception) {
            throw ReaderChangedException(e)
        }

        // The bundle's module-level functions and declarations, found by bracket depth 0
        private class Module(private val bundle: String) {
            private val depths = bracketDepths(bundle)
            private val functions = MODULE_FUNCTION_REGEX.findAll(bundle).filter { depths[it.range.first] == 0 }
                .associateBy({ it.groupValues[1] }, { it.range.first })
            private val declarations = MODULE_DECLARATION_REGEX.findAll(bundle).filter { depths[it.range.first] == 0 }
                .groupBy({ it.groupValues[1] }, { it })
            private val assignments = ASSIGNMENT_REGEX.findAll(bundle)
                .groupingBy { match -> match.groupValues.drop(1).first { it.isNotEmpty() } }
                .eachCount()

            fun aliasesOf(name: String): Set<String> = declarations.filterValues { matches ->
                matches.any { declarationAt(it).substringAfter('=') == name }
            }.keys

            // The module-level definitions [name] needs, dependencies first, skipping [provided] and locals
            fun definitions(name: String, provided: Set<String>): String = buildString {
                val seen = provided.toMutableSet()
                fun define(identifier: String) {
                    if (!seen.add(identifier)) return
                    val function = functions[identifier]
                    val source = function?.let { functionAt(bundle, it) }
                        ?: declarations[identifier]?.first()?.let { declarationAt(it) }
                        ?: return
                    // A definition assigned again elsewhere may not hold the value it starts with
                    if ((assignments[identifier] ?: 0) > (if (function == null) 1 else 0)) throw ReaderChangedException()
                    freeIdentifiers(source).forEach(::define)
                    append(source).append(";\n")
                }
                define(name)
            }

            // "var <name>=<initializer>" up to the declaration's top-level , or ;
            private fun declarationAt(match: MatchResult): String {
                val start = match.range.last + 1
                var i = start
                while (i < bundle.length && !(depths[i] == 0 && (bundle[i] == ',' || bundle[i] == ';'))) i++
                return "var ${match.groupValues[1]}=" + bundle.substring(start, i).trim()
            }
        }

        // Identifiers [code] reads that it doesn't declare, outside strings, member names and object keys
        private fun freeIdentifiers(code: String): Set<String> {
            val bare = code.replace(STRING_REGEX, "\"\"")
            val locals = LOCAL_REGEX.findAll(bare).mapNotNull { match -> match.groupValues.drop(1).firstOrNull { it.isNotEmpty() } } +
                PARAMETERS_REGEX.findAll(bare).flatMap { match ->
                    match.groupValues.drop(1).filter { it.isNotEmpty() }.flatMap { it.split(',') }
                        .mapNotNull { PARAMETER_NAME_REGEX.find(it)?.groupValues?.get(1) }
                }
            val localSet = locals.toSet()
            return IDENTIFIER_REGEX.findAll(bare)
                .filterNot { match ->
                    val before = bare.getOrNull(match.range.first - 1)
                    var next = match.range.last + 1
                    while (next < bare.length && bare[next].isWhitespace()) next++
                    before == '.' || (bare.getOrNull(next) == ':' && (before == '{' || before == ','))
                }
                .map { it.value }
                .filterNot { it in localSet }
                .toSet()
        }

        // Bracket depth at each index, skipping strings and regex literals
        private fun bracketDepths(bundle: String): IntArray {
            val depths = IntArray(bundle.length)
            var depth = 0
            var last = ' '
            var i = 0
            while (i < bundle.length) {
                depths[i] = depth
                val c = bundle[i]
                when {
                    c == '"' || c == '\'' || c == '`' -> {
                        i = skipQuoted(bundle, i, depths, depth)
                        last = c
                    }
                    c == '/' && (last in REGEX_PRECEDERS || bundle.startsWith("return", i - 6)) -> {
                        i = skipRegex(bundle, i, depths, depth)
                        last = c
                    }
                    else -> {
                        if (c == '(' || c == '[' || c == '{') depth++
                        if (c == ')' || c == ']' || c == '}') depth--
                        if (!c.isWhitespace()) last = c
                    }
                }
                i++
            }
            return depths
        }

        private const val REGEX_PRECEDERS = "(,=:[!&|?{};+-*%~^<> "

        // Index of the closing quote of the literal opening at [start]
        private fun skipQuoted(bundle: String, start: Int, depths: IntArray, depth: Int): Int {
            var i = start + 1
            while (i < bundle.length && bundle[i] != bundle[start]) {
                if (bundle[i] == '\\') i++
                if (i < bundle.length) depths[i] = depth
                i++
            }
            if (i < bundle.length) depths[i] = depth
            return i
        }

        // Index of the closing slash of the regex literal opening at [start]
        private fun skipRegex(bundle: String, start: Int, depths: IntArray, depth: Int): Int {
            var i = start + 1
            var inClass = false
            while (i < bundle.length) {
                depths[i] = depth
                when (bundle[i]) {
                    '\\' -> i++
                    '[' -> inClass = true
                    ']' -> inClass = false
                    '/' -> if (!inClass) return i
                }
                i++
            }
            return i
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
