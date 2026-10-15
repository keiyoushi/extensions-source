package keiyoushi.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CryptoTest {

    @Test
    fun testAesCbcPkcs5Padding() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b1c1d1e1f".decodeHex()
        val plaintext = "Hello, AES World in Core!".toByteArray(Charsets.UTF_8)

        val encrypted = plaintext.aesEncrypt(aesCbc(key, iv, encrypt = true))
        val decrypted = encrypted.aesDecrypt(aesCbc(key, iv))

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testAesGcmNoPadding() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b".decodeHex() // 12 bytes
        val plaintext = "GCM streaming payload".toByteArray(Charsets.UTF_8)

        val encrypted = plaintext.aesEncrypt(aesGcm(key, iv, encrypt = true))
        val decrypted = encrypted.aesDecrypt(aesGcm(key, iv))
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testAesCtrNoPadding() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b1c1d1e1f".decodeHex()
        val plaintext = "CTR stream payload".toByteArray(Charsets.UTF_8)

        val encrypted = plaintext.aesEncrypt(aesCtr(key, iv, encrypt = true))
        val decrypted = encrypted.aesDecrypt(aesCtr(key, iv))
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testAesEcbPkcs5Padding() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val plaintext = "ECB block payload test".toByteArray(Charsets.UTF_8)

        val encrypted = plaintext.aesEncrypt(aesEcb(key, encrypt = true))
        val decrypted = encrypted.aesDecrypt(aesEcb(key))
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testAesStreamingResponse() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b1c1d1e1f".decodeHex()
        val plaintext = "Streaming response body test".toByteArray(Charsets.UTF_8)
        val encrypted = plaintext.aesEncrypt(aesCbc(key, iv, encrypt = true))

        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/test.bin").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(encrypted.toResponseBody("application/octet-stream".toMediaType()))
            .build()

        val decryptedResponse = response.aesDecrypt(aesCbc(key, iv))
        assertArrayEquals(plaintext, decryptedResponse.body.bytes())
    }

    @Test
    fun testAesDecryptWithPrependedIv() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b1c1d1e1f".decodeHex()
        val plaintext = "Prepended IV streaming test".toByteArray(Charsets.UTF_8)
        val encrypted = plaintext.aesEncrypt(aesCbc(key, iv, encrypt = true))

        val combined = iv + encrypted

        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/image.bin").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(combined.toResponseBody("image/jpeg".toMediaType()))
            .build()

        val decryptedResponse = response.aesDecryptWithPrependedIv(key, ivSize = 16)
        assertArrayEquals(plaintext, decryptedResponse.body.bytes())
    }

    @Test
    fun testAesDecryptWithPrependedIvGcm() {
        val key = "000102030405060708090a0b0c0d0e0f".decodeHex()
        val iv = "101112131415161718191a1b".decodeHex() // 12 bytes
        val plaintext = "Prepended IV GCM streaming test".toByteArray(Charsets.UTF_8)
        val encrypted = plaintext.aesEncrypt(aesGcm(key, iv, encrypt = true))

        val combined = iv + encrypted

        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/image.bin").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(combined.toResponseBody("image/jpeg".toMediaType()))
            .build()

        val decryptedResponse = response.aesDecryptWithPrependedIv(ivSize = 12) { extractedIv ->
            aesGcm(key, extractedIv)
        }
        assertArrayEquals(plaintext, decryptedResponse.body.bytes())
    }
}
