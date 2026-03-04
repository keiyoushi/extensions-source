package eu.kanade.tachiyomi.extension.vi.vihentai

/**
 * Decodes viHentai's packed JavaScript to extract chapter image URLs.
 *
 * Chapter pages embed images in an obfuscated eval(function(h,u,n,t,e,r){...}(...)) script.
 * Args: h=encoded data, n=charset, t=offset, e=base & delimiter index (n[e] is delimiter).
 * Decoded output: KuroReader('#chapter-content', ["url1","url2",...], 0)
 */
object ViHentaiPacker {

    fun extractImageUrls(scriptData: String): List<String> {
        val decoded = unpack(scriptData)
        return IMAGE_URL_REGEX.findAll(decoded).map { it.groupValues[1].replace("\\/", "/") }.toList()
    }

    private fun unpack(script: String): String {
        val args = PACKED_ARGS_REGEX.find(script)
            ?: throw Exception("Could not parse packed script arguments")

        val h = args.groupValues[1]
        val n = args.groupValues[3]
        val t = args.groupValues[4].toInt()
        val e = args.groupValues[5].toInt()
        val delimiter = n[e]

        val result = StringBuilder()
        var i = 0
        while (i < h.length) {
            val s = StringBuilder()
            while (i < h.length && h[i] != delimiter) {
                s.append(h[i])
                i++
            }
            i++
            var segment = s.toString()
            for (j in n.indices) {
                segment = segment.replace(n[j].toString(), j.toString())
            }
            result.append((baseConvert(segment, e) - t).toChar())
        }
        return result.toString()
    }

    private fun baseConvert(d: String, fromBase: Int): Int {
        val chars = BASE_CHARSET.substring(0, fromBase)
        return d.reversed().foldIndexed(0) { idx, acc, c ->
            val pos = chars.indexOf(c)
            if (pos != -1) acc + pos * Math.pow(fromBase.toDouble(), idx.toDouble()).toInt() else acc
        }
    }

    private const val BASE_CHARSET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/"
    private val IMAGE_URL_REGEX = Regex(""""(https?:\\?/\\?/[^"]+\.\w{3,4})""")
    private val PACKED_ARGS_REGEX = Regex("""\}\("(.+)",\s*(\d+),\s*"([^"]+)",\s*(\d+),\s*(\d+),\s*(\d+)\)""")
}
