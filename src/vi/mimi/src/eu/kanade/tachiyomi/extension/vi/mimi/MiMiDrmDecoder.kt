package eu.kanade.tachiyomi.extension.vi.mimi

import kotlin.experimental.xor

object MiMiDrmDecoder {

    class Tile(
        val srcX: Int,
        val srcY: Int,
        val dstX: Int,
        val dstY: Int,
        val width: Int,
        val height: Int,
    )

    data class DecodedMap(
        val sourceWidth: Int,
        val sourceHeight: Int,
        val tiles: List<Tile>,
    )

    private data class Segment(
        val id: String,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val sourceId: String,
    )

    private val mapRegex = Regex(
        pattern = """v1\|sw:(\d+)\|sh:(\d+)\|((?:[0-2]{2}@\d+,\d+,\d+,\d+>[0-2]{2}\|){8}[0-2]{2}@\d+,\d+,\d+,\d+>[0-2]{2})""",
    )

    private val segmentRegex = Regex("""([0-2]{2})@(\d+),(\d+),(\d+),(\d+)>([0-2]{2})""")

    private val keyTable = arrayOf(
        "10.094534846668065",
        "7.830415197347441",
        "16.99376503124865",
        "13.206661543266259",
        "7.316826787559291",
        "10.4581449488877",
        "4.175296661012279",
        "10.175873934720146",
        "16.434397649190988",
        "7.009874458739787",
        "13.575803014637726",
        "29.279163189766738",
        "10.750231018960623",
        "10.094342559715047",
        "28.658921501338497",
        "25.793772667060153",
        "25.79379811121803",
        "15.748609882695796",
        "7.534001429117513",
        "28.907337185559953",
        "13.22733409213105",
        "7.266890610739514",
        "6.669662254093193",
        "13.227334074999675",
        "28.564557448091602",
        "16.619459066493555",
        "6.969300123013573",
        "26.138465628985216",
        "13.317787084345925",
        "19.228026822727582",
        "10.772577818410019",
        "3.7994766625978458",
        "29.188688520919868",
        "16.369262643760873",
        "7.631192793297872",
        "22.635116664169104",
        "7.008299254805293",
        "19.918386626762093",
        "10.432972563129333",
        "4.367499602056042",
        "26.166382731558237",
        "16.342370610615042",
        "7.515015438908234",
        "29.295296241376956",
        "32.16934026452751",
        "4.177784547778614",
        "4.159160201118592",
        "10.436068860553476",
        "4.1529681276331845",
        "10.436068612003677",
    )

    private const val FALLBACK_HIGH_KEY = "3.8672468480107685"
    private const val FALLBACK_EMPTY_KEY = "10.094534846668065"

    fun decodeMap(drm: String): DecodedMap? {
        val payload = drm.dropLast(2)
        if (payload.isEmpty() || payload.length % 2 != 0) return null

        val cipher = parseHex(payload) ?: return null
        val key = deriveKey(drm).toByteArray(Charsets.UTF_8)
        val plain = ByteArray(cipher.size)

        for (i in cipher.indices) {
            plain[i] = cipher[i] xor key[i % key.size]
        }

        val text = plain.toString(Charsets.UTF_8)
        val mapMatch = mapRegex.find(text) ?: return null

        val sourceWidth = mapMatch.groupValues[1].toIntOrNull() ?: return null
        val sourceHeight = mapMatch.groupValues[2].toIntOrNull() ?: return null
        val rawSegments = mapMatch.groupValues[3].split('|')

        val segments = rawSegments.map { segmentText ->
            val match = segmentRegex.matchEntire(segmentText) ?: return null
            Segment(
                id = match.groupValues[1],
                x = match.groupValues[2].toIntOrNull() ?: return null,
                y = match.groupValues[3].toIntOrNull() ?: return null,
                width = match.groupValues[4].toIntOrNull() ?: return null,
                height = match.groupValues[5].toIntOrNull() ?: return null,
                sourceId = match.groupValues[6],
            )
        }

        if (segments.size != 9) return null

        val rectById = segments.associateBy { it.id }
        val tiles = segments.map { dst ->
            val src = rectById[dst.sourceId] ?: return null
            Tile(
                srcX = src.x,
                srcY = src.y,
                dstX = dst.x,
                dstY = dst.y,
                width = dst.width,
                height = dst.height,
            )
        }

        return DecodedMap(
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            tiles = tiles,
        )
    }

    private fun parseHex(hex: String): ByteArray? {
        val bytes = ByteArray(hex.length / 2)
        var byteIndex = 0
        var i = 0
        while (i < hex.length) {
            val high = hexNibble(hex[i])
            val low = hexNibble(hex[i + 1])
            if (high < 0 || low < 0) return null
            bytes[byteIndex++] = ((high shl 4) or low).toByte()
            i += 2
        }
        return bytes
    }

    private fun hexNibble(ch: Char): Int = when (ch) {
        in '0'..'9' -> ch - '0'
        in 'a'..'f' -> ch - 'a' + 10
        in 'A'..'F' -> ch - 'A' + 10
        else -> -1
    }

    private fun deriveKey(drm: String): String {
        if (drm.isEmpty()) return FALLBACK_EMPTY_KEY

        val last = decimalDigit(drm.last())
        val hasSecond = drm.length >= 2
        if (!hasSecond) {
            if (last in 0..49) return keyTable[last]
            if (last >= 0) return FALLBACK_HIGH_KEY
            return FALLBACK_EMPTY_KEY
        }

        val prev = decimalDigit(drm[drm.length - 2])
        val index = (if (prev >= 0) prev * 10 else 0) + if (last >= 0) last else 0

        return when {
            index in 0..49 -> keyTable[index]
            else -> FALLBACK_HIGH_KEY
        }
    }

    private fun decimalDigit(ch: Char): Int {
        val value = ch.code - '0'.code
        return if (value in 0..9) value else -1
    }
}
