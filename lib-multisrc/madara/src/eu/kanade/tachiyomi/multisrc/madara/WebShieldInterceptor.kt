package eu.kanade.tachiyomi.multisrc.madara

import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Answers the Imunify360 WebShield "One moment, please..." page the way its script does in a browser. */
internal class WebShieldInterceptor(
    private val errorMessage: String,
    private val client: () -> OkHttpClient,
) : Interceptor {

    // Host to when its check was last answered, and to when an answer last failed
    private val solvedAt = HashMap<String, TimeSource.Monotonic.ValueTimeMark>()
    private val failedAt = HashMap<String, TimeSource.Monotonic.ValueTimeMark>()

    // The answer redirects to the challenged url, which the interceptor retries itself
    private val answerClient by lazy {
        client().newBuilder()
            .followRedirects(false)
            .apply { interceptors().removeAll { it is WebShieldInterceptor } }
            .addNetworkInterceptor(::hostOnlyCookie)
            .build()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val sentAt = TimeSource.Monotonic.markNow()
        val response = chain.proceed(request)
        val challenge = response.webShieldPage()
        if (challenge == null && response.code != UNSUPPORTED_MEDIA_TYPE) return response
        response.close()
        synchronized(solvedAt) {
            if (failedAt[host].let { it != null && it.elapsedNow() < FAILURE_PAUSE }) throw IOException(errorMessage)
            if (solvedAt[host].let { it == null || it < sentAt }) {
                val page = challenge ?: navigationPage(request)
                if (page == null || !answer(request, page)) fail(host)
                solvedAt[host] = TimeSource.Monotonic.markNow()
            }
        }
        val retry = chain.proceed(request)
        if (retry.webShieldPage() != null || retry.code == UNSUPPORTED_MEDIA_TYPE) {
            retry.close()
            synchronized(solvedAt) { fail(host) }
        }
        return retry
    }

    private fun fail(host: String): Nothing {
        failedAt[host] = TimeSource.Monotonic.markNow()
        throw IOException(errorMessage)
    }

    private fun answer(request: Request, challenge: String): Boolean {
        val answerUrl = answerUrl(challenge, request.url) ?: return false
        val answer = request.newBuilder()
            .url(answerUrl)
            .get()
            .removeHeader("X-Requested-With")
            .fetch(Fetch.DOCUMENT.from(request.url))
            .build()
        answerClient.newCall(answer).execute().close()
        return true
    }

    // Image hosts answer image requests with 415 and show their check only to a navigation
    private fun navigationPage(request: Request): String? {
        val page = request.newBuilder().get().removeHeader("X-Requested-With").fetch(Fetch.DOCUMENT).build()
        return answerClient.newCall(page).execute().use { it.webShieldPage() }
    }

    // Every host answers with the same cookie name, so a parent-domain cookie would shadow the image hosts' own
    private fun hostOnlyCookie(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val cookies = response.headers("Set-Cookie").ifEmpty { return response }
        return response.newBuilder()
            .removeHeader("Set-Cookie")
            .apply { cookies.forEach { addHeader("Set-Cookie", it.replace(DOMAIN_ATTRIBUTE, "")) } }
            .build()
    }

    private fun Response.webShieldPage(): String? {
        if (body.contentType()?.subtype != "html") return null
        return peekBody(32L * 1024).string().takeIf { "wsidchk" in it }
    }

    private fun answerUrl(html: String, page: HttpUrl): HttpUrl? {
        val action = ACTION.find(html)?.value ?: return null
        val wsidchk = jsFuckNumbers(html).takeIf { it.size == 2 }?.sum() ?: return null
        val ts = TS.find(html)?.groupValues?.get(1) ?: return null
        val pdata = PDATA.find(html)?.groupValues?.get(1) ?: return null
        val id = formId(html) ?: return null

        return page.newBuilder()
            .encodedPath(action)
            .query(null)
            .addQueryParameter("wsidchk", wsidchk.toString())
            .addQueryParameter("pdata", pdata)
            .addQueryParameter("id", id)
            .addQueryParameter("ts", ts)
            .addQueryParameter("cttl", "0")
            .build()
    }

    // `+((+!+[]+!![])+(+![]+[])...)`: groups folded with JS `+`, a group ending in `+[]` being a string
    private fun jsFuckNumbers(html: String): List<Long> = buildList {
        var start = html.indexOf(JSFUCK_START)
        while (start >= 0) {
            val end = closingParen(html, start + 1) ?: break
            val expression = html.substring(start + 2, end)
            var number = 0L
            var text: String? = null
            var group = expression.indexOf('(')
            while (group >= 0) {
                val groupEnd = closingParen(expression, group) ?: break
                val tokens = expression.substring(group + 1, groupEnd)
                val trues = tokens.split("!![]").size - 1
                val digit = trues + if (tokens.startsWith("+!+[]")) 1 else 0
                val isString = tokens.split("[]").size - 1 - trues >= 2
                when {
                    text != null -> text += digit
                    isString -> text = "$number$digit"
                    else -> number += digit
                }
                group = expression.indexOf('(', groupEnd)
            }
            add(text?.toLongOrNull() ?: number)
            start = html.indexOf(JSFUCK_START, end)
        }
    }

    private fun closingParen(text: String, open: Int): Int? {
        var depth = 0
        for (i in open until text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> if (--depth == 0) return i
            }
        }
        return null
    }

    // The form's id: decoded string-table entries and inline hex parts
    private fun formId(html: String): String? {
        val decoder = StringDecoder.from(html) ?: return null
        val expression = FORM_ID.find(html)?.groupValues?.get(1) ?: return null
        return expression.split('+').map { decoder.resolve(it) ?: return null }.joinToString("")
    }

    // The obfuscator's string table, rotated as the page's own startup loop rotates it
    private class StringDecoder(
        private val table: List<String>,
        private val offset: Int,
        private val constants: Map<String, Map<String, Int>>,
    ) {
        fun decode(index: Int): String = table[(index - offset).mod(table.size)]

        fun resolve(token: String): String? {
            if (token.startsWith("'")) return token.trim('\'')
            val argument = DECODE_CALL.find(token)?.groupValues?.get(1) ?: return null
            return decode(index(argument) ?: return null)
        }

        // `0x1a2` or a key of a constants object, `a0d.G`
        fun index(argument: String): Int? {
            if (argument.startsWith("0x")) return argument.substring(2).toIntOrNull(16)
            val (name, key) = argument.split('.').takeIf { it.size == 2 } ?: return null
            return constants[name]?.get(key)
        }

        // `parseInt(e(..))/1+...` with each call replaced by its leading integer, as JS parseInt reads it
        fun checksum(expression: String): Double? {
            val arithmetic = buildString {
                var last = 0
                for (call in PARSE_INT.findAll(expression)) {
                    append(expression, last, call.range.first)
                    val leading = LEADING_INT.find(decode(index(call.groupValues[1]) ?: return null))?.value ?: "NaN"
                    append('(').append(leading).append(')')
                    last = call.range.last + 1
                }
                append(expression, last, expression.length)
            }
            return Arithmetic(arithmetic).evaluate()
        }

        companion object {
            fun from(html: String): StringDecoder? {
                val startup = STARTUP_CALL.find(html) ?: return null
                val target = startup.groupValues[2].toLongOrNull(16)?.toDouble() ?: return null
                val tableFunction = Regex("""function ${Regex.escape(startup.groupValues[1])}\(\)\{[^\[]*(\[(?:'(?:[^'\\]|\\.)*',?)+\])""")
                val table = tableFunction.find(html)?.groupValues?.get(1)
                    ?.let { QUOTED.findAll(it).map { entry -> entry.groupValues[1] }.toList() }
                    ?: return null
                val offset = OFFSET.find(html)?.groupValues?.get(2)?.toIntOrNull(16) ?: return null
                val expression = CHECKSUM.find(html)?.groupValues?.get(1) ?: return null
                val constants = CONSTANTS.findAll(html).associate { match ->
                    match.groupValues[1] to CONSTANT.findAll(match.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2].toInt(16) }
                }
                // Each left rotation of the table is one step back of the offset
                return table.indices.asSequence()
                    .map { StringDecoder(table, offset - it, constants) }
                    .firstOrNull { it.checksum(expression) == target }
            }
        }
    }

    // `+ - * /`, unary signs and parentheses over integers, in JS double arithmetic
    private class Arithmetic(private val text: String) {
        private var position = 0

        fun evaluate(): Double = sum()

        private fun sum(): Double {
            var value = product()
            while (true) {
                value = when {
                    eat('+') -> value + product()
                    eat('-') -> value - product()
                    else -> return value
                }
            }
        }

        private fun product(): Double {
            var value = factor()
            while (true) {
                value = when {
                    eat('*') -> value * factor()
                    eat('/') -> value / factor()
                    else -> return value
                }
            }
        }

        private fun factor(): Double = when {
            eat('+') -> factor()
            eat('-') -> -factor()
            eat('(') -> sum().also { eat(')') }
            else -> number()
        }

        private fun number(): Double {
            val match = NUMBER.matchAt(text, position) ?: return Double.NaN
            position = match.range.last + 1
            return when {
                match.value == "NaN" -> Double.NaN
                match.value.startsWith("0x") -> match.value.substring(2).toLong(16).toDouble()
                else -> match.value.toDouble()
            }
        }

        private fun eat(char: Char): Boolean {
            if (position < text.length && text[position] == char) {
                position++
                return true
            }
            return false
        }
    }

    companion object {
        private const val UNSUPPORTED_MEDIA_TYPE = 415
        private val FAILURE_PAUSE = 60.seconds
        private val DOMAIN_ATTRIBUTE = Regex(""";\s*Domain=[^;]*""", RegexOption.IGNORE_CASE)
        private const val JSFUCK_START = "+((+!+[]"

        private val ACTION = Regex("""/z[0-9a-f]{40}""")
        private val TS = Regex("""'ts','(\d+)'""")
        private val PDATA = Regex("""='(https?%3[Aa]%2[Ff]%2[Ff][^']*)'""")

        private val STARTUP_CALL = Regex("""\}\(([A-Za-z0-9_$]+),0x([0-9a-f]+)\)""")
        private val OFFSET = Regex("""([A-Za-z0-9_$]+)=\1-0x([0-9a-f]+);""")
        private val CHECKSUM = Regex("""var [A-Za-z0-9_$]+=(parseInt[^;]*);if\(""")
        private val CONSTANTS = Regex("""([A-Za-z0-9_$]+)=\{([A-Za-z0-9_$]+:0x[0-9a-f]+(?:,[A-Za-z0-9_$]+:0x[0-9a-f]+)*)\}""")
        private val CONSTANT = Regex("""([A-Za-z0-9_$]+):0x([0-9a-f]+)""")
        private val QUOTED = Regex("""'((?:[^'\\]|\\.)*)'""")
        private val PARSE_INT = Regex("""parseInt\([A-Za-z0-9_$]+\(([A-Za-z0-9_$.]+)\)\)""")
        private val LEADING_INT = Regex("""^[+-]?\d+""")
        private val NUMBER = Regex("""0x[0-9a-f]+|\d+|NaN""")
        private val DECODE_CALL = Regex("""^[A-Za-z0-9_$]+\(([A-Za-z0-9_$.]+)\)$""")
        private const val DECODE = """[A-Za-z0-9_$]+\((?:0x[0-9a-f]+|[A-Za-z0-9_$]+\.[A-Za-z0-9_$]+)\)"""
        private const val LITERAL = """'[0-9a-f]*'"""
        private val FORM_ID = Regex("""\[[^\]]*\]='id',[A-Za-z0-9_$]+\[[^\]]*\]=((?:$DECODE|$LITERAL)(?:\+(?:$DECODE|$LITERAL))*)""")
    }
}
