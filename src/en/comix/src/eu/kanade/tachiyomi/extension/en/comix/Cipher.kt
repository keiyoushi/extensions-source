package eu.kanade.tachiyomi.extension.en.comix

import android.util.Base64
import kotlinx.serialization.Serializable

@Serializable
class WebViewCapture(
    val payload: String,
    val material: CipherMaterial? = null,
)

@Serializable
class CipherMaterial(
    val sboxes: List<List<Int>>,
    val keys: List<List<Int>>,
) {
    fun isValid() = sboxes.size == 3 &&
        sboxes.all { it.size == 256 } &&
        keys.size == 3 &&
        keys.all { it.isNotEmpty() }
}

@Serializable
class EncryptedResponse(
    val e: String,
)

class ComixCipher(material: CipherMaterial) {
    private val sboxes = material.sboxes.map { values -> values.toIntArray() }
    private val keys = material.keys.map { values -> values.toIntArray() }

    init {
        require(material.isValid()) { "Invalid Comix cipher material" }
    }

    fun sign(path: String, query: String): String {
        var data = buildString {
            append(path.removePrefix("/api/v1"))
            if (query.isNotEmpty()) append("?$query")
        }.toByteArray()

        repeat(3) { round ->
            data = substitute(data, sboxes[round], keys[round], PREVIOUS[round])
        }

        return Base64.encodeToString(data, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decrypt(value: String): String {
        var data = Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        for (round in 2 downTo 0) {
            data = substituteInverse(data, sboxes[round], keys[round], PREVIOUS[round])
        }

        return data.toString(Charsets.UTF_8)
    }

    private fun substitute(data: ByteArray, sbox: IntArray, key: IntArray, previous: Int): ByteArray {
        val output = ByteArray(data.size)
        var prev = previous
        data.indices.forEach { index ->
            val substituted = sbox[(data[index].toInt() and 0xff) xor key[index % key.size] xor prev]
            output[index] = substituted.toByte()
            prev = substituted
        }
        return output
    }

    private fun substituteInverse(data: ByteArray, sbox: IntArray, key: IntArray, previous: Int): ByteArray {
        val inverse = IntArray(256)
        sbox.indices.forEach { inverse[sbox[it]] = it }

        val output = ByteArray(data.size)
        var prev = previous
        data.indices.forEach { index ->
            val value = data[index].toInt() and 0xff
            output[index] = (inverse[value] xor key[index % key.size] xor prev).toByte()
            prev = value
        }
        return output
    }

    companion object {
        private val PREVIOUS = intArrayOf(189, 133, 32)
    }
}
