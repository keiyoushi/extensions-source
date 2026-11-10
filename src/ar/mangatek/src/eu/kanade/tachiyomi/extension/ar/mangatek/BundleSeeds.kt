package eu.kanade.tachiyomi.extension.ar.mangatek

import android.util.Base64
import app.cash.quickjs.QuickJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256

// Reads the reader's two rotating seeds (proof salt, reader seed N0) out of the obfuscated
// /_astro/ChapterImageViewer.<hash>.js. The bundle keeps its strings in one string array, rotated
// at load, read through a decoder behind chains of offset wrappers. Each seed is a concatenation
// of such lookups: N0 at top level, the salt inside a function. The array, decoder and rotation
// run in QuickJS; the wrappers are resolved here because their names are reused per scope.
// The WASM module is a concatenation too; its hash checks the decoding and that the algorithm
// the port implements is still the one the bundle uses.
object BundleSeeds {

    class Seeds(val proofSalt: String, val readerSeed: String)

    fun extract(js: String): Seeds = Bundle(js).seeds()

    // A call wrapper(p0, p1) forwards to target(p[index] + delta, …)
    private class Wrapper(val position: Int, val scope: IntRange, val target: String, val args: List<Pair<Int, Int>>)

    private class Site(val position: Int, val expression: String)

    private class Bundle(val js: String) {
        private val blocks = blocks(js)
        private val closeOf = blocks.associate { it.first to it.last }
        private val openOf = blocks.associate { it.last to it.first }

        // Functions shaped like a wrapper but with other arguments are not wrappers
        private val wrappers = WRAPPER_REGEX.findAll(js).mapNotNull { match ->
            val (name, first, second, target, argList) = match.destructured
            val args = argList.split(',').map { arg ->
                val parts = WRAPPER_ARG_REGEX.matchEntire(arg.trim()) ?: return@mapNotNull null
                val (param, sign, amount) = parts.destructured
                val index = when (param) {
                    first -> 0
                    second -> 1
                    else -> return@mapNotNull null
                }
                val delta = amount.toIntOrNull()?.let { if (sign == "-") -it else it } ?: 0
                index to delta
            }
            name to Wrapper(match.range.first, scopeOf(match.range.first), target, args)
        }.groupBy({ it.first }, { it.second })

        private val decoderName: String
        private val prelude: String

        init {
            val array = ARRAY_REGEX.find(js) ?: error("No string array in the reader script")
            val arrayName = array.groupValues[1]
            val decoder = DECODER_REGEX.findAll(js).firstOrNull { it.groupValues[3] == arrayName }
                ?: error("No string decoder in the reader script")
            decoderName = decoder.groupValues[1]
            val rotation = ROTATION_CALL_REGEX.findAll(js).firstOrNull { it.groupValues[1] == arrayName }
                ?: error("No string array rotation in the reader script")
            prelude = listOf(
                functionAt(array.range.first),
                functionAt(decoder.range.first),
                rotationAt(rotation.range.first),
            ).joinToString(";\n", postfix = ";\n")
        }

        fun seeds(): Seeds {
            val sites = concatSites()
            val script = sites.joinToString(",", prefix = "$prelude JSON.stringify([", postfix = "])") {
                "(() => { try { return String(${it.expression}) } catch (e) { return null } })()"
            }
            val result = QuickJs.create().use { it.evaluate(script) } as? String
                ?: error("Reader script strings did not evaluate")
            val values = result.parseAs<List<String?>>().zip(sites)
                .mapNotNull { (value, site) -> value?.let { site.position to it } }

            val wasm = values.firstOrNull { it.second.startsWith(WASM_BASE64_PREFIX) }?.second
                ?: error("No WASM module in the reader script (its strings did not decode)")
            require(Base64.decode(wasm, Base64.DEFAULT).sha256().toHexString() == WASM_SHA256) {
                "The reader script's WASM module did not match"
            }

            val (topLevel, nested) = values.filter { HEX48_REGEX.matches(it.second) }
                .partition { depth(it.first) == 0 }
            val readerSeed = topLevel.singleOrNull()?.second
                ?: error("Expected one top-level seed in the reader script, found ${topLevel.size}")
            val proofSalt = nested.singleOrNull()?.second
                ?: error("Expected one nested seed in the reader script, found ${nested.size}")
            return Seeds(proofSalt, readerSeed)
        }

        // `function name(…){…}` starting at `start`
        private fun functionAt(start: Int): String {
            val open = js.indexOf('{', start)
            val close = closeOf[open] ?: error("Unbalanced function in the reader script")
            return js.substring(start, close + 1)
        }

        // `(function(…){…})(array,…)` whose body closes at `close`
        private fun rotationAt(close: Int): String {
            val open = openOf[close] ?: error("Unbalanced rotation in the reader script")
            val start = js.lastIndexOf("(function", open)
            require(start >= 0) { "Unexpected rotation in the reader script" }
            var index = close + 2
            var parens = 0
            do {
                when (js[index]) {
                    '(' -> parens++
                    ')' -> parens--
                }
                index++
            } while (parens > 0)
            // The rotation loops until its checksum matches; bound it so a changed array cannot hang
            // QuickJS (a wrong rotation then fails the WASM check)
            val rotation = js.substring(start, index)
            require(ROTATION_LOOP in rotation) { "Unexpected rotation loop in the reader script" }
            return rotation.replaceFirst(ROTATION_LOOP, ROTATION_GUARD)
        }

        // Runs of lookups joined by `+`, optionally grouped in parentheses: a(1,2)+(b(3,4)+"x")+…,
        // as decoder(…) calls. Scanned by hand: a regex with a repeated group overflows the stack on
        // the WASM's ~1000 pieces. A run starts at a call.
        private fun concatSites(): List<Site> {
            val sites = ArrayList<Site>()
            var start = CALL_REGEX.find(js)
            while (start != null) {
                if (start.range.first > 0 && js[start.range.first - 1] in IDENTIFIER_OR_DOT) {
                    start = start.next()
                    continue
                }
                val pieces = arrayListOf(start)
                var end = start.range.last + 1
                while (true) {
                    var index = end
                    while (index < js.length && js[index] == ')') index++
                    if (index >= js.length || js[index] != '+') break
                    index++
                    while (index < js.length && js[index] == '(') index++
                    val next = PIECE_REGEX.matchAt(js, index) ?: break
                    pieces += next
                    end = next.range.last + 1
                }
                if (pieces.size > 1) {
                    val parts = pieces.map { piece ->
                        if (piece.value[0] == '"' || piece.value[0] == '\'') {
                            piece.value
                        } else {
                            val (name, first, second) = piece.destructured
                            resolve(name, listOf(first, second), piece.range.first)
                        }
                    }
                    // a run with a call that is not a string lookup is not a seed
                    if (parts.all { it != null }) sites += Site(start.range.first, parts.joinToString("+"))
                }
                start = CALL_REGEX.find(js, end)
            }
            return sites
        }

        // name(args) at `position`, followed through the innermost wrapper in scope to the decoder
        private fun resolve(name: String, args: List<String>, position: Int): String? {
            var target = name
            var current = args
            var from = position
            repeat(MAX_WRAPPER_DEPTH) {
                val wrapper = wrappers[target]?.filter { from in it.scope }?.minByOrNull { it.scope.last - it.scope.first }
                    ?: return if (target == decoderName) "$decoderName(${current.joinToString(",")})" else null
                current = wrapper.args.map { (index, delta) -> offset(current[index], delta) ?: return null }
                target = wrapper.target
                from = wrapper.position
            }
            return null
        }

        private fun offset(arg: String, delta: Int): String? = when {
            delta == 0 -> arg
            else -> arg.toDoubleOrNull()?.let { (it.toLong() + delta).toString() }
        }

        private fun scopeOf(position: Int): IntRange = blocks.filter { position > it.first && position < it.last }
            .minByOrNull { it.last - it.first } ?: js.indices

        private fun depth(position: Int): Int = blocks.count { position > it.first && position < it.last }
    }

    // Brace pairs of the source, skipping strings, templates, regex literals and comments
    private fun blocks(js: String): List<IntRange> {
        val pairs = ArrayList<IntRange>()
        val stack = ArrayDeque<Int>() // open-brace index, or -1 for a template `${`
        var i = 0
        var previous = ';'
        fun skipTemplate() {
            while (i < js.length) {
                when (js[i]) {
                    '\\' -> i += 2
                    '`' -> {
                        i++
                        return
                    }
                    '$' -> if (js.getOrNull(i + 1) == '{') {
                        stack.addLast(-1)
                        i += 2
                        return
                    } else {
                        i++
                    }
                    else -> i++
                }
            }
        }
        while (i < js.length) {
            val c = js[i]
            when {
                c == '"' || c == '\'' -> {
                    i++
                    while (i < js.length && js[i] != c) i += if (js[i] == '\\') 2 else 1
                    i++
                    previous = 'a'
                }
                c == '`' -> {
                    i++
                    skipTemplate()
                    previous = 'a'
                }
                c == '/' && js.getOrNull(i + 1) == '/' -> i = js.indexOf('\n', i).let { if (it < 0) js.length else it }
                c == '/' && js.getOrNull(i + 1) == '*' -> i = js.indexOf("*/", i + 2).let { if (it < 0) js.length else it + 2 }
                c == '/' && previous in REGEX_PRECEDERS -> {
                    i++
                    var inClass = false
                    while (i < js.length) {
                        when (js[i]) {
                            '\\' -> i++
                            '[' -> inClass = true
                            ']' -> inClass = false
                            '/' -> if (!inClass) break
                        }
                        i++
                    }
                    i++
                    while (i < js.length && js[i].isLetter()) i++
                    previous = 'a'
                }
                c == '{' -> {
                    stack.addLast(i)
                    i++
                    previous = c
                }
                c == '}' -> {
                    val open = stack.removeLastOrNull()
                    i++
                    if (open == -1) {
                        skipTemplate()
                    } else if (open != null) {
                        pairs += open..(i - 1)
                    }
                    previous = c
                }
                else -> {
                    if (!c.isWhitespace()) previous = c
                    i++
                }
            }
        }
        return pairs
    }

    private const val WASM_SHA256 = "7ae024b07b1264939707262242c6810b570b0bc1051c01c35dcd864e9bdecd2e"
    private const val WASM_BASE64_PREFIX = "AGFzbQ" // "\u0000asm"
    private const val MAX_WRAPPER_DEPTH = 32
    private const val ROTATION_LOOP = "for(;;)"
    private const val ROTATION_GUARD = "for(let rotationGuard=0;rotationGuard<10000;rotationGuard++)"
    private const val IDENTIFIER_OR_DOT = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_$."
    private const val REGEX_PRECEDERS = "(,=:[!&|?{};+-*%<>~^"

    private const val ARG = """-?\d+(?:e\d+)?|"[^"\\]*"|'[^'\\]*'"""
    private const val STRING = """"[^"\\]*"|'[^'\\]*'"""
    private val ARRAY_REGEX = Regex("""function ([\w$]+)\(\)\{const [\w$]+=\[""")
    private val DECODER_REGEX = Regex("""function ([\w$]+)\(([\w$]+),[\w$]+\)\{\2=\2-[^;]+;const [\w$]+=([\w$]+)\(\)""")
    private val ROTATION_CALL_REGEX = Regex("""\}\)\(([\w$]+),""")
    private val WRAPPER_REGEX = Regex("""function ([\w$]+)\(([\w$]+),([\w$]+)\)\{return ([\w$]+)\(([^()]*)\)\}""")
    private val WRAPPER_ARG_REGEX = Regex("""([\w$]+)(?:\s*([+-])\s*(-?\d+))?""")
    private val CALL_REGEX = Regex("""([\w$]+)\(($ARG),($ARG)\)""")
    private val PIECE_REGEX = Regex("""([\w$]+)\(($ARG),($ARG)\)|$STRING""")
    private val HEX48_REGEX = Regex("""[0-9a-f]{48}""")
}
