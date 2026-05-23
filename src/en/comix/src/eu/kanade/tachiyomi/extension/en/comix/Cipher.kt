package eu.kanade.tachiyomi.extension.en.comix

import android.util.Base64
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * 3-Pass CFB Substitution Cipher used by Comix to encrypt API responses.
 *
 * Each pass applies:
 *   output[i] = S[input[i]] XOR key[i % keyLen] XOR feedback
 *   feedback  = input[i]
 *
 * Pass 1 takes ciphertext, Pass 2 takes Pass 1 output, Pass 3 produces plaintext.
 * Parameters are tied to the JS bundle version and must be updated if it changes.
 */
object Cipher {

    val interceptor = Interceptor { chain ->
        val response = chain.proceed(chain.request())
        if (response.header("x-enc") != "1") return@Interceptor response

        val encrypted = response.parseAs<EncryptedResponse>()
        val ciphertext = Base64.decode(encrypted.e, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val plaintext = decrypt(ciphertext)

        response.newBuilder()
            .body(String(plaintext).toResponseBody(JSON_MEDIA))
            .removeHeader("x-enc")
            .build()
    }

    private val JSON_MEDIA = "application/json".toMediaType()

    fun decrypt(enc: ByteArray): ByteArray {
        val n = enc.size

        // Pass 1: S1, K19
        val v1 = ByteArray(n)
        var fb = FB1
        for (i in 0 until n) {
            val b = enc[i].toInt() and 0xFF
            v1[i] = (S1[b] xor K19[i % 19] xor fb).toByte()
            fb = b
        }

        // Pass 2: S2, K28
        val v2 = ByteArray(n)
        fb = FB2
        for (i in 0 until n) {
            val b = v1[i].toInt() and 0xFF
            v2[i] = (S2[b] xor K28[i % 28] xor fb).toByte()
            fb = b
        }

        // Pass 3: S3, K26
        val out = ByteArray(n)
        fb = FB3
        for (i in 0 until n) {
            val b = v2[i].toInt() and 0xFF
            out[i] = (S3[b] xor K26[i % 26] xor fb).toByte()
            fb = b
        }

        return out
    }

    private const val FB1 = 0x8E
    private const val FB2 = 0x6F
    private const val FB3 = 0x91

    private fun hex(s: String) = s.chunked(2).map { it.toInt(16) }.toIntArray()

    private val K19 = hex("2124e518244056c89bab6e6b7b4f4ec127898a")
    private val K28 = hex("24e9e90cf319263b7f5752dccbf689cf8763fb66cee4e0c13aa47c55")
    private val K26 = hex("ac70da61e0a7a4fd1629dad78958550b22e5c7d52af8540cb627")

    // @formatter:off
    private val S1 = hex(
        "b1a242690ca6d475a40085107a6d0f04" +
            "8f7214f08324669d5af5fc58e58dd122" +
            "ace411571bb035403dc2267d76dc70fa" +
            "8655c9ad2b03fda0cfc53408ec56d23b" +
            "4677330523c1dee043079fab47981399" +
            "e28e68b53cb9c8c7512ab8f7530acd5f" +
            "5402c371fe496fbfb34162a8780ed096" +
            "0d279ba928be2cda5c1cd9eeb67c741d" +
            "8bbddf3ff295128a829aca84604ee948" +
            "8c6b6ae8f1b4bbf9afce80e33a09b29c" +
            "374f3e9ef48836c0fb18d6632e1ed820" +
            "cc442da767737f89a3cb59640bea5b52" +
            "15934af38190ffdddbaebad3501ae6ed" +
            "efd58791b792295d45eb257ef6e70121" +
            "173297a1c494a54c165ed7f838300661" +
            "bcc679e16e4b4d652f1f7baa39196c31",
    )

    private val S2 = hex(
        "898575876e5027c873543cbb5d9e32bc" +
            "ab2208814958292a3bdc062426457d59" +
            "56ce38d653180079b828694f744788fa" +
            "1539e4427af386daaf4b17a4f133ed3f" +
            "631be2559291bd5146be676b971a8a96" +
            "b1379b9c4382c7112d10d02084f9907e" +
            "9db4f548098f4c34ecfe6aa135e10ef0" +
            "0fb3f75c05d561a257306cd7ffeb76cc" +
            "a92b36f866b0b70b7b2cd1f2727c9fd8" +
            "df60de6494a6e744eedd16e907c6216d" +
            "a31c03accbea0cd35f31787ff66280c4" +
            "98b6e6fdc123cdad0a02bf3d5a14e52f" +
            "9a01708c3a19f41de3fb4e99a52e3ed4" +
            "77a85bd2dbc593c0aa4071a052ef12a7" +
            "ca4de01f8b6f41bac3048d8e68fcb295" +
            "e8b5b9aec9251e13d983c265cf5e0d4a",
    )

    private val S3 = hex(
        "b9e0b5fabacff26116d3b8ab7b1768e2" +
            "1b9a2edff0aab70fa4ff0183a36279fe" +
            "88c4d20a6b23f4654e3e328fbb334fc8" +
            "865c39ea978cf519af14ce3a6ad5c627" +
            "6e22ae8e187628c30e0846420036ac6d" +
            "7c2f6626b4247d15c9fcc799de9ebc7f" +
            "f68b48cbd0c206e1a6133451e5f7fbad" +
            "dcdd381d4374f32181b16cb67e45c1c5" +
            "eced3d58db441e022c89057a95504af8" +
            "b02a825bd607a0fd8aef1aa1f1d86375" +
            "a2e73771565aca6735ee118730914ba5" +
            "25a89d729409690d5fa993e31064e93f" +
            "4d1f600cd7eb701257529cbf9b84b2be" +
            "da3c2d40548578bde6d996e4cc90f92b" +
            "c073e89f9277d1b3034131d40b80491c" +
            "5d04988d5947a73b5e206f5329554ccd",
    )
    // @formatter:on
}

@Serializable
private class EncryptedResponse(val e: String)
