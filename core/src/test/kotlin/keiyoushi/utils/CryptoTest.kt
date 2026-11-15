package keiyoushi.utils

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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

    @Test
    fun testDigests() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", "".md5().toHexString())
        assertEquals("900150983cd24fb0d6963f7d28e17f72", "abc".md5().toHexString())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".sha256().toHexString(),
        )
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            "abc".sha512().toHexString(),
        )
        assertArrayEquals("日本語".toByteArray(Charsets.UTF_8).sha256(), "日本語".sha256())
    }

    @Test
    fun testHmacSha256() {
        // RFC 4231 test case 1
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            "Hi There".encodeToByteArray().hmacSha256(ByteArray(20) { 0x0b }).toHexString(),
        )
        // RFC 4231 test case 2
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            "what do ya want for nothing?".hmacSha256("Jefe".encodeToByteArray()).toHexString(),
        )
    }

    @Test
    fun testEvpBytesToKey() {
        // openssl enc -aes-128-cbc -md md5 -S 0102030405060708 -pass pass:secret -P
        val (key, iv) = evpBytesToKey("secret".encodeToByteArray(), "0102030405060708".decodeHex(), keyLen = 16, ivLen = 16)
        assertEquals("c9e5a1bd216dbe1317e230cef48f38ee", key.toHexString())
        assertEquals("7f0e17ad64022144bccec4a1aa2879ab", iv.toHexString())
    }

    @Test
    fun testCryptoJsDecrypt() {
        // printf 'Hello, CryptoJS!' | openssl enc -aes-256-cbc -md md5 -pass pass:secret -base64
        val payload = java.util.Base64.getDecoder().decode("U2FsdGVkX18ks2hXA0V9h0iEJt3uLcXhs5HgKM25zFZvk5XdG2Hw+YjirlLB9sFo")
        assertEquals("Hello, CryptoJS!", payload.cryptoJsDecrypt("secret").decodeToString())
    }
}
