package eu.kanade.tachiyomi.extension.ar.mangastarz

import okhttp3.HttpUrl

/** The request the Imunify360 WebShield "One moment, please..." page's script sends from a browser. */
object WebShield {

    fun isChallenge(html: String) = "wsidchk" in html

    fun challengeUrl(html: String, page: HttpUrl): HttpUrl? {
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

    // The form's id: a string-table entry followed by inline hex parts
    private fun formId(html: String): String? {
        val match = FORM_ID.find(html) ?: return null
        val tail = HEX_PART.findAll(match.groupValues[2]).joinToString("") { it.groupValues[1] }
        return stringTableEntry(html, match.groupValues[1].toIntOrNull(16) ?: return null)?.plus(tail)
    }

    // The obfuscator's table is rotated at load; `window[decode(N)]` is "navigator", which anchors the offset
    private fun stringTableEntry(html: String, index: Int): String? {
        val table = STRING_TABLE.findAll(html).firstOrNull { "'navigator'" in it.value }
            ?.let { QUOTED.findAll(it.value).map { entry -> entry.groupValues[1] }.toList() }
            ?: return null
        val navigatorPosition = table.indexOf("navigator")
        val navigatorIndex = NAVIGATOR.find(html)?.groupValues?.get(1)?.toIntOrNull(16) ?: return null
        return table[(index - navigatorIndex + navigatorPosition).mod(table.size)]
    }

    private const val JSFUCK_START = "+((+!+[]"
    private val ACTION = Regex("""/z[0-9a-f]{40}""")
    private val TS = Regex("""'ts','(\d+)'""")
    private val PDATA = Regex("""='(https?%3[Aa]%2[Ff]%2[Ff][^']*)'""")
    private val FORM_ID = Regex("""=[A-Za-z0-9_]+\(0x([0-9a-f]+)\)((?:\+'[0-9a-f]+')+)""")
    private val HEX_PART = Regex("""'([0-9a-f]+)'""")
    private val NAVIGATOR = Regex("""window\[[A-Za-z0-9_]+\(0x([0-9a-f]+)\)\]""")
    private val STRING_TABLE = Regex("""\[(?:'(?:[^'\\]|\\.)*',?)+\]""")
    private val QUOTED = Regex("""'((?:[^'\\]|\\.)*)'""")
}
