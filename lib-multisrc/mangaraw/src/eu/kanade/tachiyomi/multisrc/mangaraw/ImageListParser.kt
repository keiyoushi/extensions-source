package eu.kanade.tachiyomi.multisrc.mangaraw

import kotlin.math.pow

class ImageListParser(
    html: String,
    private val position: Int,
    private val keys: String = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=",
    private val pattern: String = """'BYFxAcGcC4.*?'""",
) {

    private val code: String
    init {
        code = getCode(html)
    }

    fun getImageList(): List<String>? {
        val charMap = mutableMapOf<Int, String?>()
        for (j in 0..2) {
            charMap[j] = j.toString()
        }

        val data = Data(getValue(0), position, 1)
        charMap[3] = when (getCharCode(data, position, 4)) {
            0 -> charCodeToString(getCharCode(data, position, 256))
            1 -> charCodeToString(getCharCode(data, position, 65536))
            2 -> return null
            else -> null
        }

        val imageCharList = mutableListOf(charMap[3])

        var counter = 4
        var charaIndexCounter = 4
        var n = 3.0
        var cash = charMap[3]
        while (data.index <= code.length) {
            val max = 2.0.pow(n).toInt()

            val charIndex = when (val charCode = getCharCode(data, position, max)) {
                0 -> {
                    charMap[charaIndexCounter] = charCodeToString(getCharCode(data, position, 256))
                    counter--
                    charaIndexCounter++
                }
                1 -> {
                    charMap[charaIndexCounter] = charCodeToString(getCharCode(data, position, 65536))
                    counter--
                    charaIndexCounter++
                }
                2 -> {
                    return imageCharList.joinToString("").split(",")
                }
                else -> {
                    charCode
                }
            }

            val char = if (!charMap[charIndex].isNullOrEmpty()) {
                charMap[charIndex]!!
            } else if (charIndex != charaIndexCounter) {
                return null
            } else {
                cash + cash?.charAt(0)
            }

            if (counter == 0) {
                counter = 2.0.pow(n++).toInt()
            }

            imageCharList.add(char)

            charMap[charaIndexCounter++] = cash + char.charAt(0)
            counter--
            cash = char
            if (counter == 0) {
                counter = 2.0.pow(n++).toInt()
            }
        }

        return null
    }

    private data class Data(var value: Int, var position: Int, var index: Int)

    private fun getCharCode(data: Data, position: Int, max: Int): Int {
        var charIndex = 0

        var i = 1
        while (i != max) {
            val tmp = data.value and data.position
            data.position = data.position shr 1
            if (data.position == 0) {
                data.position = position
                data.value = getValue(data.index++)
            }

            charIndex = charIndex or ((if (tmp > 0) 1 else 0) * i)
            i = i shl 1
        }

        return charIndex
    }

    fun getValue(index: Int): Int {
        return getValueByChar(code.charAt(index))
    }

    private fun getCode(html: String): String {
        val regex = Regex(pattern)
        return regex.find(html)!!.value.replace("'", "").replace("\\", "").replace("u002b", "+")
    }

    private fun getValueByChar(char: String): Int {
        return keys.indexOf(char)
    }

    companion object {

        private fun String.charAt(index: Int): String {
            return substring(index, index + 1)
        }

        private fun charCodeToString(charCode: Int): String {
            return charCode.toChar().toString()
        }
    }
}
