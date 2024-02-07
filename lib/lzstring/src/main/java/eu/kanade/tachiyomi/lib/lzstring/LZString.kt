package eu.kanade.tachiyomi.lib.lzstring

typealias getCharFromIntFn = (it: Int) -> String
typealias getNextValueFn = (it: Int) -> Int

/**
 * Reimplementation of [lz-string](https://github.com/pieroxy/lz-string) compression/decompression.
 */
object LZString {
    private fun compress(
        uncompressed: String,
        bitsPerChar: Int,
        getCharFromInt: getCharFromIntFn,
    ): String {
        val context = CompressionContext(uncompressed.length, bitsPerChar, getCharFromInt)

        for (ii in uncompressed.indices) {
            context.c = uncompressed[ii].toString()

            if (!context.dictionary.containsKey(context.c)) {
                context.dictionary[context.c] = context.dictSize++
                context.dictionaryToCreate[context.c] = true
            }

            context.wc = context.w + context.c

            if (context.dictionary.containsKey(context.wc)) {
                context.w = context.wc
                continue
            }

            context.outputCodeForW()

            context.decrementEnlargeIn()
            context.dictionary[context.wc] = context.dictSize++
            context.w = context.c
        }

        if (context.w.isNotEmpty()) {
            context.outputCodeForW()
            context.decrementEnlargeIn()
        }

        // Mark the end of the stream
        context.value = 2
        for (i in 0 until context.numBits) {
            context.dataVal = (context.dataVal shl 1) or (context.value and 1)
            context.appendDataOrAdvancePosition()
            context.value = context.value shr 1
        }

        while (true) {
            context.dataVal = context.dataVal shl 1

            if (context.dataPosition == bitsPerChar - 1) {
                context.data.append(getCharFromInt(context.dataVal))
                break
            }

            context.dataPosition++
        }

        return context.data.toString()
    }

    private fun decompress(length: Int, resetValue: Int, getNextValue: getNextValueFn): String {
        val dictionary = mutableListOf<String>()
        val result = StringBuilder()
        val data = DecompressionContext(resetValue, getNextValue)
        var enlargeIn = 4
        var numBits = 3
        var entry: String
        var c: Char? = null

        for (i in 0 until 3) {
            dictionary.add(i.toString())
        }

        data.loopUntilMaxPower()

        when (data.bits) {
            0 -> {
                data.bits = 0
                data.maxPower = 1 shl 8
                data.power = 1
                data.loopUntilMaxPower()
                c = data.bits.toChar()
            }
            1 -> {
                data.bits = 0
                data.maxPower = 1 shl 16
                data.power = 1
                data.loopUntilMaxPower()
                c = data.bits.toChar()
            }
            2 -> throw IllegalArgumentException("Invalid LZString")
        }

        if (c == null) {
            throw Exception("No character found")
        }

        dictionary.add(c.toString())
        var w = c.toString()
        result.append(c.toString())

        while (true) {
            if (data.index > length) {
                throw IllegalArgumentException("Invalid LZString")
            }

            data.bits = 0
            data.maxPower = 1 shl numBits
            data.power = 1
            data.loopUntilMaxPower()

            var cc = data.bits

            when (data.bits) {
                0 -> {
                    data.bits = 0
                    data.maxPower = 1 shl 8
                    data.power = 1
                    data.loopUntilMaxPower()
                    dictionary.add(data.bits.toChar().toString())
                    cc = dictionary.size - 1
                    enlargeIn--
                }
                1 -> {
                    data.bits = 0
                    data.maxPower = 1 shl 16
                    data.power = 1
                    data.loopUntilMaxPower()
                    dictionary.add(data.bits.toChar().toString())
                    cc = dictionary.size - 1
                    enlargeIn--
                }
                2 -> return result.toString()
            }

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }

            entry = if (cc < dictionary.size) {
                dictionary[cc]
            } else {
                if (cc == dictionary.size) {
                    w + w[0]
                } else {
                    throw Exception("Invalid LZString")
                }
            }
            result.append(entry)
            dictionary.add(w + entry[0])
            enlargeIn--
            w = entry

            if (enlargeIn == 0) {
                enlargeIn = 1 shl numBits
                numBits++
            }
        }
    }

    private const val base64KeyStr = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/="

    fun compressToBase64(input: String): String =
        compress(input, 6) { base64KeyStr[it].toString() }.let {
            return when (it.length % 4) {
                0 -> it
                1 -> "$it==="
                2 -> "$it=="
                3 -> "$it="
                else -> throw IllegalStateException("Modulo of 4 should not exceed 3.")
            }
        }

    fun decompressFromBase64(input: String): String =
        decompress(input.length, 32) {
            base64KeyStr.indexOf(input[it])
        }
}

private data class DecompressionContext(
    val resetValue: Int,
    val getNextValue: getNextValueFn,
    var value: Int = getNextValue(0),
    var position: Int = resetValue,
    var index: Int = 1,
    var bits: Int = 0,
    var maxPower: Int = 1 shl 2,
    var power: Int = 1,
) {
    fun loopUntilMaxPower() {
        while (power != maxPower) {
            val resb = value and position

            position = position shr 1

            if (position == 0) {
                position = resetValue
                value = getNextValue(index++)
            }

            bits = bits or ((if (resb > 0) 1 else 0) * power)
            power = power shl 1
        }
    }
}

private data class CompressionContext(
    val uncompressedLength: Int,
    val bitsPerChar: Int,
    val getCharFromInt: getCharFromIntFn,
    var value: Int = 0,
    val dictionary: MutableMap<String, Int> = HashMap(),
    val dictionaryToCreate: MutableMap<String, Boolean> = HashMap(),
    var c: String = "",
    var wc: String = "",
    var w: String = "",
    var enlargeIn: Int = 2,  // Compensate for the first entry which should not count
    var dictSize: Int = 3,
    var numBits: Int = 2,
    val data: StringBuilder = StringBuilder(uncompressedLength / 3),
    var dataVal: Int = 0,
    var dataPosition: Int = 0,
) {
    fun appendDataOrAdvancePosition() {
        if (dataPosition == bitsPerChar - 1) {
            dataPosition = 0
            data.append(getCharFromInt(dataVal))
            dataVal = 0
        } else {
            dataPosition++
        }
    }

    fun decrementEnlargeIn() {
        enlargeIn--
        if (enlargeIn == 0) {
            enlargeIn = 1 shl numBits
            numBits++
        }
    }

    // Output the code for W.
    fun outputCodeForW() {
        if (dictionaryToCreate.containsKey(w)) {
            if (w[0].code < 256) {
                for (i in 0 until numBits) {
                    dataVal = dataVal shl 1
                    appendDataOrAdvancePosition()
                }

                value = w[0].code

                for (i in 0 until 8) {
                    dataVal = (dataVal shl 1) or (value and 1)
                    appendDataOrAdvancePosition()
                    value = value shr 1
                }
            } else {
                value = 1

                for (i in 0 until numBits) {
                    dataVal = (dataVal shl 1) or value
                    appendDataOrAdvancePosition()
                    value = 0
                }

                value = w[0].code

                for (i in 0 until 16) {
                    dataVal = (dataVal shl 1) or (value and 1)
                    appendDataOrAdvancePosition()
                    value = value shr 1
                }
            }

            decrementEnlargeIn()
            dictionaryToCreate.remove(w)
        } else {
            value = dictionary[w]!!

            for (i in 0 until numBits) {
                dataVal = (dataVal shl 1) or (value and 1)
                appendDataOrAdvancePosition()
                value = value shr 1
            }
        }
    }
}
