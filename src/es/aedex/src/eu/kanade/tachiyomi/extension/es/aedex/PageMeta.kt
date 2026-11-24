package eu.kanade.tachiyomi.extension.es.aedex

import keiyoushi.utils.sha256

// The page's own scramble settings, sent as "key|xor|gutter" masked with two hashes of the visit's session key and the page handle
class PageMeta(val key: String, val xor: Int?, val gutter: Int?) {
    companion object {
        private const val MASK_LENGTH = 64
        private const val MAX_LENGTH = 250

        fun unmask(metaHex: String?, sessionKey: String, handle: String): PageMeta? {
            if (metaHex == null || metaHex.length < 4 || metaHex.length % 2 != 0) return null
            val mask = "$sessionKey:m0:$handle".sha256() + "$sessionKey:m1:$handle".sha256()
            val length = minOf(MAX_LENGTH, metaHex.length / 2)
            val plain = String(
                CharArray(length) { i ->
                    (metaHex.substring(i * 2, i * 2 + 2).toInt(16) xor mask[i % MASK_LENGTH].toInt()).and(0xff).toChar()
                },
            )
            val fields = plain.split('|')
            val key = fields[0].takeIf { it.isNotEmpty() && fields.size >= 2 } ?: return null
            return PageMeta(
                key,
                fields[1].filter(Char::isDigit).toIntOrNull()?.takeIf { it in 1..255 },
                fields.getOrNull(2)?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in 1..63 },
            )
        }
    }
}
