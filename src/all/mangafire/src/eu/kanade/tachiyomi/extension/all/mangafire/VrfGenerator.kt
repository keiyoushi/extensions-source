package eu.kanade.tachiyomi.extension.all.mangafire
import android.util.Base64

/**
 * Original script by @Trung0246 on Github
 */
object VrfGenerator {
    private fun atob(data: String): ByteArray = Base64.decode(data, Base64.DEFAULT)
    private fun btoa(data: ByteArray): String = Base64.encodeToString(data, Base64.DEFAULT)

    private fun rc4(key: ByteArray, input: ByteArray): ByteArray {
        val s = IntArray(256) { it }
        var j = 0

        // KSA
        for (i in 0..255) {
            j = (j + s[i] + key[i % key.size].toInt().and(0xFF)) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }

        // PRGA
        val output = ByteArray(input.size)
        var i = 0
        j = 0
        for (y in input.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            val k = s[(s[i] + s[j]) and 0xFF]
            output[y] = (input[y].toInt() xor k).toByte()
        }
        return output
    }

    private fun transform(
        input: ByteArray,
        initSeedBytes: ByteArray,
        prefixKeyBytes: ByteArray,
        prefixLen: Int,
        schedule: List<(Int) -> Int>,
    ): ByteArray {
        val out = mutableListOf<Byte>()
        for (i in input.indices) {
            if (i < prefixLen) {
                out.add(prefixKeyBytes[i])
            }
            val transformed = schedule[i % 10](
                (input[i].toInt() xor initSeedBytes[i % 32].toInt()) and 0xFF,
            ) and 0xFF
            out.add(transformed.toByte())
        }
        return out.toByteArray()
    }

    private val scheduleC = listOf<(Int) -> Int>(
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c xor 241) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
    )

    private val scheduleY = listOf<(Int) -> Int>(
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c xor 163) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
    )

    private val scheduleB = listOf<(Int) -> Int>(
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c - 48 + 256) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c xor 163) and 0xFF },
    )

    private val scheduleJ = listOf<(Int) -> Int>(
        { c -> (c + 223) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> (c - 19 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c + 223) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 83) and 0xFF },
    )

    private val scheduleE = listOf<(Int) -> Int>(
        { c -> (c + 82) and 0xFF },
        { c -> (c xor 83) and 0xFF },
        { c -> (c xor 163) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c - 170 + 256) and 0xFF },
        { c -> (c xor 8) and 0xFF },
        { c -> (c xor 241) and 0xFF },
        { c -> (c + 82) and 0xFF },
        { c -> (c + 176) and 0xFF },
        { c -> ((c shl 4) or (c ushr 4)) and 0xFF },
    )

    private val rc4Keys = mapOf(
        "l" to "u8cBwTi1CM4XE3BkwG5Ble3AxWgnhKiXD9Cr279yNW0=",
        "g" to "t00NOJ/Fl3wZtez1xU6/YvcWDoXzjrDHJLL2r/IWgcY=",
        "B" to "S7I+968ZY4Fo3sLVNH/ExCNq7gjuOHjSRgSqh6SsPJc=",
        "m" to "7D4Q8i8dApRj6UWxXbIBEa1UqvjI+8W0UvPH9talJK8=",
        "F" to "0JsmfWZA1kwZeWLk5gfV5g41lwLL72wHbam5ZPfnOVE=",
    )

    private val seeds32 = mapOf(
        "A" to "pGjzSCtS4izckNAOhrY5unJnO2E1VbrU+tXRYG24vTo=",
        "V" to "dFcKX9Qpu7mt/AD6mb1QF4w+KqHTKmdiqp7penubAKI=",
        "N" to "owp1QIY/kBiRWrRn9TLN2CdZsLeejzHhfJwdiQMjg3w=",
        "P" to "H1XbRvXOvZAhyyPaO68vgIUgdAHn68Y6mrwkpIpEue8=",
        "k" to "2Nmobf/mpQ7+Dxq1/olPSDj3xV8PZkPbKaucJvVckL0=",
    )

    private val prefixKeys = mapOf(
        "O" to "Rowe+rg/0g==",
        "v" to "8cULcnOMJVY8AA==",
        "L" to "n2+Og2Gth8Hh",
        "p" to "aRpvzH+yoA==",
        "W" to "ZB4oBi0=",
    )

    fun generate(input: String): String {
        var bytes = input.toByteArray()
        // RC4 1
        bytes = rc4(atob(rc4Keys["l"]!!), bytes)

        // Step C1
        bytes = transform(bytes, atob(seeds32["A"]!!), atob(prefixKeys["O"]!!), 7, scheduleC)

        // RC4 2
        bytes = rc4(atob(rc4Keys["g"]!!), bytes)

        // Step Y
        bytes = transform(bytes, atob(seeds32["V"]!!), atob(prefixKeys["v"]!!), 10, scheduleY)

        // RC4 3
        bytes = rc4(atob(rc4Keys["B"]!!), bytes)

        // Step B
        bytes = transform(bytes, atob(seeds32["N"]!!), atob(prefixKeys["L"]!!), 9, scheduleB)

        // RC4 4
        bytes = rc4(atob(rc4Keys["m"]!!), bytes)

        // Step J
        bytes = transform(bytes, atob(seeds32["P"]!!), atob(prefixKeys["p"]!!), 7, scheduleJ)

        // RC4 5
        bytes = rc4(atob(rc4Keys["F"]!!), bytes)

        // Step E
        bytes = transform(bytes, atob(seeds32["k"]!!), atob(prefixKeys["W"]!!), 5, scheduleE)

        // Base64URL encode
        return btoa(bytes)
            .replace("+", "-")
            .replace("/", "_")
            .replace("=", "")
    }
}
