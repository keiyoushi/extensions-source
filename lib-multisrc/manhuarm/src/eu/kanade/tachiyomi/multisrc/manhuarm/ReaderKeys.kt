package eu.kanade.tachiyomi.multisrc.manhuarm

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import okhttp3.Headers
import okio.Buffer
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// The keys the chapter page's reader derives from its one-time parameters and its challenge answer
class ReaderKeys private constructor(
    private val params: ReaderParamsDto,
    private val answer: Long,
    private val splitKey: String,
) {
    val endpoint = params.endpoint

    val initRequest = InitRequestDto("init", params.payload)

    // The reader subtracts these from each OCR box
    val boxOffset = intArrayOf(
        (answer and 0xFF).toInt() + 19,
        (answer shr 8 and 0xFF).toInt() + 37,
        (answer shr 16 and 0xFF).toInt() + 13,
        (answer shr 24 and 0xFF).toInt() + 29,
    )

    fun initHeaders(base: Headers): Headers = headers(base, proof("$answer|${params.nonce}|${params.seed}|$splitKey|v9-chk"))

    fun decryptInit(sealed: SealedDto): ChapterDataDto {
        val key = "v9|${params.seed}|$answer|$splitKey|${params.nonce}|${params.timestamp}".sha256()
        val cipher = aesGcm(key, Base64.decode(sealed.iv, Base64.DEFAULT))
        val data = Base64.decode(sealed.data, Base64.DEFAULT) + Base64.decode(sealed.tag, Base64.DEFAULT)
        return String(data.aesDecrypt(cipher)).parseAs()
    }

    fun imageHeaders(base: Headers, page: ManifestEntryDto): Headers = headers(base, proof("$answer|${page.imageKey}|${page.imageSeed}|${params.nonce}|v9-img"))

    // The body is the IV (12 bytes), the tag (16) and the ciphertext of the image cut into 16 shuffled chunks
    fun decryptImage(body: ByteArray, page: ManifestEntryDto): Buffer {
        val key = "v9-ib|$answer|${page.imageKey}|${page.imageSeed}|$splitKey|${params.nonce}".sha256()
        val cipher = aesGcm(key, body.copyOfRange(0, 12))
        // GCM takes the tag after the ciphertext
        val tag = body.copyOfRange(12, 28)
        body.copyInto(body, 12, 28)
        tag.copyInto(body, body.size - 16)
        return unshuffle(cipher.doFinal(body, 12, body.size - 12), answer xor page.imageSeed)
    }

    private fun proof(text: String) = text.sha256().toHexString()

    private fun headers(base: Headers, proof: String) = base.newBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .set("X-Mrm-Sig", params.signature)
        .set("X-Mrm-Nonce", params.nonce)
        .set("X-Mrm-Seed", params.seed)
        .set("X-Mrm-Ts", params.timestamp.toString())
        .set("X-Mrm-Proof", proof)
        .build()

    companion object {
        fun from(document: Document): ReaderKeys {
            val script = document.select("script").map(Element::data).first { "const _v9" in it }
            val params = PARAMS_REGEX.find(script)!!.groupValues[1].parseAs<ReaderParamsDto>()
            // The last rule wins, as in the browser
            val rule = document.select("style").flatMap { CSS_RULE_REGEX.findAll(it.data()) }.last().groupValues[1]
            val keyElement = document.selectFirst("#mrm-d2")!!
            val domSalt = document.selectFirst("#mrm-d1")!!.attr("data-u").toLong(16) and 0xFFFF shl 16 or
                (keyElement.attr("data-v").toLong(16) and 0xFFFF)
            val splitKey = (CSS_KEY_REGEX.find(rule)!!.groupValues[1] + keyElement.attr("data-w")).lowercase()
            val salts = mapOf(
                "ds" to domSalt,
                "cs" to Z_INDEX_REGEX.find(rule)!!.groupValues[1].toLong(),
                "cvs" to canvasSalt(params.canvasSeed),
                "rkh" to splitKey.substring(0, 8).toLong(16),
                "rkl" to splitKey.substring(8, 16).toLong(16),
            )
            val answer = Challenge(salts).run(CHALLENGE_REGEX.find(script)!!.groupValues[1])
            return ReaderKeys(params, answer, splitKey)
        }

        private val PARAMS_REGEX = Regex("""const _v9\s*=\s*(\{.*?\});""")
        private val CHALLENGE_REGEX = Regex("""function [\w$]+\(ds,cs,cvs,rkh,rkl\)\{([^}]*)\}""")
        private val CSS_RULE_REGEX = Regex("""#mrm-c1\s*\{([^}]*)\}""")
        private val Z_INDEX_REGEX = Regex("""z-index:\s*(\d+)""")
        private val CSS_KEY_REGEX = Regex("""--mrm-k1:\s*["']([0-9a-fA-F]{8})["']""")
    }
}

private const val UINT32 = 0xFFFFFFFFL

private fun xorShift32(seed: Long): () -> Long {
    var state = (seed and UINT32).takeIf { it != 0L } ?: 1L
    return {
        state = state xor (state shl 13 and UINT32)
        state = state xor (state ushr 17)
        state = state xor (state shl 5 and UINT32)
        state
    }
}

// The reader draws 24 seeded runs of flat colours on a 16x1 canvas and hashes the pixels back; the colours are exact
private fun canvasSalt(seed: Long): Long {
    val next = xorShift32(seed)
    val cells = IntArray(16)
    repeat(24) {
        val shift = (next() % 4).toInt()
        val x = (next() % (16 - shift)).toInt() + shift
        val width = (next() % 3).toInt() + 1
        val cell = ((next() % 4) shl 4 or ((next() % 4) shl 2) or (next() % 4)).toInt()
        for (pixel in x until minOf(x + width, 16)) cells[pixel] = cell
    }
    return cells.fold(0x811C9DC5L) { hash, cell -> (hash * 1664525L + cell + 1013904223L) and UINT32 }
}

// Chunk i of the shuffled image is chunk order[i] of the image; a remainder stays last
private fun unshuffle(bytes: ByteArray, seed: Long): Buffer {
    val next = xorShift32(seed)
    val order = IntArray(16) { it }
    for (i in 15 downTo 1) {
        val j = (next() % (i + 1)).toInt()
        order[i] = order[j].also { order[j] = order[i] }
    }
    val source = IntArray(16)
    order.forEachIndexed { index, target -> source[target] = index }
    val chunk = bytes.size / 16
    return Buffer().apply {
        source.forEach { write(bytes, it * chunk, chunk) }
        write(bytes, 16 * chunk, bytes.size - 16 * chunk)
    }
}

/**
 * The reader's challenge function, rewritten on every page load: straight-line statements over the salts, a
 * three-word state `r` and `Math.imul`, every value an unsigned 32-bit integer.
 */
private class Challenge(salts: Map<String, Long>) {
    private val variables = salts.mapValuesTo(HashMap()) { it.value and UINT32 }
    private val state = LongArray(3)

    fun run(body: String): Long {
        for (statement in body.split(';')) {
            val text = statement.trim().removePrefix("var ")
            if (text.startsWith("return ")) return Expression(text.removePrefix("return ")).value()
            topLevel(text).forEach(::assign)
        }
        error("Challenge has no return")
    }

    private fun assign(part: String) {
        if (IDENTIFIER_REGEX.matches(part)) return
        val (target, value) = ASSIGNMENT_REGEX.matchEntire(part)?.destructured ?: error("Unsupported challenge statement")
        when {
            target == "r" -> topLevel(value.trim().removeSurrounding("[", "]")).forEachIndexed { i, item -> state[i] = Expression(item).value() }
            target.startsWith("r[") -> state[target[2].digitToInt()] = Expression(value).value()
            else -> variables[target] = Expression(value).value()
        }
    }

    private fun topLevel(text: String): List<String> = buildList {
        var depth = 0
        var start = 0
        text.forEachIndexed { i, char ->
            when (char) {
                '(', '[' -> depth++
                ')', ']' -> depth--
                ',' -> if (depth == 0) {
                    add(text.substring(start, i).trim())
                    start = i + 1
                }
            }
        }
        text.substring(start).trim().takeIf(String::isNotEmpty)?.let(::add)
    }

    private inner class Expression(text: String) {
        private val tokens = TOKEN_REGEX.findAll(text).map { it.value }.toList()
        private var position = 0

        init {
            check(tokens.sumOf(String::length) == text.count { !it.isWhitespace() }) { "Unsupported challenge expression" }
        }

        fun value(): Long = or().also { check(position == tokens.size) { "Unsupported challenge expression" } }

        private fun peek() = tokens.getOrNull(position)

        private fun take(expected: String? = null): String = tokens[position++].also {
            check(expected == null || it == expected) { "Unsupported challenge expression" }
        }

        private fun or(): Long {
            var value = xor()
            while (peek() == "|") {
                take()
                value = value or xor()
            }
            return value
        }

        private fun xor(): Long {
            var value = and()
            while (peek() == "^") {
                take()
                value = value xor and()
            }
            return value
        }

        private fun and(): Long {
            var value = shift()
            while (peek() == "&") {
                take()
                value = value and shift()
            }
            return value
        }

        private fun shift(): Long {
            var value = additive()
            while (peek() == "<<" || peek() == ">>>") {
                val operator = take()
                val amount = (additive() and 31).toInt()
                value = if (operator == "<<") value shl amount and UINT32 else value ushr amount
            }
            return value
        }

        private fun additive(): Long {
            var value = unary()
            while (peek() == "+" || peek() == "-") {
                val operator = take()
                val operand = unary()
                value = (if (operator == "+") value + operand else value - operand) and UINT32
            }
            return value
        }

        private fun unary(): Long = if (peek() == "-") {
            take()
            -unary() and UINT32
        } else {
            primary()
        }

        private fun primary(): Long {
            val token = take()
            return when {
                token == "(" -> or().also { take(")") }
                token == "Math.imul" -> {
                    take("(")
                    val left = or()
                    take(",")
                    val right = or()
                    take(")")
                    left * right and UINT32
                }
                token == "r" -> {
                    take("[")
                    state[or().toInt()].also { take("]") }
                }
                token[0].isDigit() -> token.toLong() and UINT32
                else -> variables[token] ?: error("Unsupported challenge variable")
            }
        }
    }

    companion object {
        private val TOKEN_REGEX = Regex("""Math\.imul|[A-Za-z_$][\w$]*|\d+|>>>|<<|[-+^|&()\[\],]""")
        private val IDENTIFIER_REGEX = Regex("""[A-Za-z_$][\w$]*""")
        private val ASSIGNMENT_REGEX = Regex("""(r\[\d]|r|[A-Za-z_$][\w$]*)\s*=(.*)""", RegexOption.DOT_MATCHES_ALL)
    }
}
