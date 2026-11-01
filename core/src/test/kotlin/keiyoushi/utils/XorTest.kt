package keiyoushi.utils

import keiyoushi.network.XorInterceptor
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.buffer
import okio.source
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class XorTest {

    @Test
    fun testByteArrayXorInPlaceMutatesOriginal() {
        val original = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val result = original.xorInPlace(0x55.toByte())
        assertArrayEquals(original, result)
        assertEquals(0x01 xor 0x55, result[0].toInt())
        assertEquals(0x02 xor 0x55, result[1].toInt())

        // Roundtrip reverses in-place
        original.xorInPlace(0x55.toByte())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), original)
    }

    @Test
    fun testByteArrayXorImmutabilityAndCorrectness() {
        val original = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val copy = original.copyOf()
        val result = original.xor(0xFF)

        assertNotSame(original, result)
        assertArrayEquals(copy, original) // original unchanged
        assertEquals((0x10 xor 0xFF).toByte(), result[0])
        assertEquals((0x20 xor 0xFF).toByte(), result[1])

        // Roundtrip on copy
        val roundtrip = result.xor(0xFF)
        assertArrayEquals(original, roundtrip)
    }

    @Test
    fun testByteArrayMultiByteXor() {
        val data = "Hello, World! Testing multi-byte XOR encryption.".toByteArray(Charsets.UTF_8)
        val key = byteArrayOf(0x12, 0x34, 0x56, 0x78)

        val encrypted = data.xor(key)
        assertNotSame(data, encrypted)

        val decrypted = encrypted.xor(key)
        assertArrayEquals(data, decrypted)
    }

    @Test
    fun testByteArrayXorEmptyKeyThrows() {
        try {
            byteArrayOf(1, 2, 3).xor(byteArrayOf())
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }

        try {
            byteArrayOf(1, 2, 3).xorInPlace(byteArrayOf())
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun testSourceXorEmptyKeyThrows() {
        try {
            Buffer().xor(byteArrayOf())
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("empty"))
        }
    }

    @Test
    fun testSingleByteXorSmallSource() {
        val input = byteArrayOf(10, 20, 30, 40, 50)
        val key = 0xAA.toByte()

        val source = Buffer().write(input).xor(key)
        val output = Buffer()
        source.read(output, 1024)

        val expected = input.xor(key)
        assertArrayEquals(expected, output.readByteArray())
    }

    @Test
    fun testSingleByteXorMultiSegmentSource() {
        // Okio segments are 8192 bytes. Test a payload spanning multiple segments.
        val size = 25000
        val input = ByteArray(size) { (it % 251).toByte() }
        val key = 0x7A.toByte()

        val source = ByteArrayInputStream(input).source().xor(key)
        val buffered = source.buffer()
        val result = buffered.readByteArray()

        assertArrayEquals(input.xor(key), result)
    }

    @Test
    fun testSingleByteXorChunkedReadsWithExistingSinkData() {
        val input = ByteArray(1000) { (it % 256).toByte() }
        val key = 42.toByte()

        val source = Buffer().write(input).xor(key)
        val sink = Buffer()

        // Pre-fill sink to ensure offset calculations (sink.size - read) work correctly
        sink.writeUtf8("PRE_EXISTING_PREFIX_")
        val prefixBytes = "PRE_EXISTING_PREFIX_".toByteArray()

        // Read in small chunks
        while (source.read(sink, 128) != -1L) {
            // keep reading
        }

        val prefixRead = sink.readByteArray(prefixBytes.size.toLong())
        assertArrayEquals(prefixBytes, prefixRead)

        val decryptedPayload = sink.readByteArray()
        assertArrayEquals(input.xor(key), decryptedPayload)
    }

    @Test
    fun testMultiByteXorSingleByteDelegation() {
        val input = byteArrayOf(1, 2, 3, 4, 5)
        val key = byteArrayOf(0x33)

        val result = Buffer().write(input).xor(key).buffer().readByteArray()
        assertArrayEquals(input.xor(0x33), result)
    }

    @Test
    fun testMultiByteXorMultiSegmentAndUnevenChunks() {
        val size = 30000
        val input = ByteArray(size) { (it * 7 % 255).toByte() }
        val key = "secret_key_12345".toByteArray(Charsets.UTF_8)

        val source = ByteArrayInputStream(input).source().xor(key)
        val sink = Buffer()

        // Read with arbitrary uneven chunk sizes
        val chunkSizes = longArrayOf(17, 1024, 7, 8192, 4096, 333)
        var i = 0
        while (true) {
            val chunk = chunkSizes[i % chunkSizes.size]
            val read = source.read(sink, chunk)
            if (read == -1L) break
            i++
        }

        val result = sink.readByteArray()
        assertArrayEquals(input.xor(key), result)
    }

    @Test
    fun testResponseXorSingleByte() {
        val payload = "Hello Image Stream".toByteArray()
        val key = 0x5C.toByte()
        val mediaType = "image/png".toMediaType()

        val response = Response.Builder()
            .request(Request.Builder().url("https://example.com/test.png").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(payload.toResponseBody(mediaType))
            .build()

        val transformed = response.xor(key)
        assertEquals(mediaType, transformed.body.contentType())
        assertEquals(payload.size.toLong(), transformed.body.contentLength())

        val result = transformed.body.bytes()
        assertArrayEquals(payload.xor(key), result)
    }

    @Test
    fun testXorInterceptorSuccess() {
        val plainText = "Decrypted manga page payload"
        val key = byteArrayOf(0x12, 0x34, 0xAB.toByte())
        val encrypted = plainText.toByteArray().xor(key)

        val client = OkHttpClient.Builder()
            .addInterceptor(XorInterceptor { it.url.fragment?.decodeHex() })
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(encrypted.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                },
            )
            .build()

        val request = Request.Builder()
            .url("https://example.com/page.jpg#1234ab")
            .build()

        val response = client.newCall(request).execute()
        assertEquals(plainText, response.body.string())
    }

    @Test
    fun testXorInterceptorNullKeyPassesThrough() {
        val payload = "unencrypted payload"
        val client = OkHttpClient.Builder()
            .addInterceptor(XorInterceptor { it.url.fragment?.decodeHex() })
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(payload.toResponseBody())
                        .build()
                },
            )
            .build()

        val request = Request.Builder().url("https://example.com/page.jpg").build()
        val response = client.newCall(request).execute()
        assertEquals(payload, response.body.string())
    }

    @Test
    fun testXorInterceptorErrorResponsePassesThrough() {
        val client = OkHttpClient.Builder()
            .addInterceptor(XorInterceptor { it.url.fragment?.decodeHex() })
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("Not Found Error".toResponseBody())
                        .build()
                },
            )
            .build()

        val request = Request.Builder().url("https://example.com/page.jpg#aabbcc").build()
        val response = client.newCall(request).execute()
        assertEquals(404, response.code)
        assertEquals("Not Found Error", response.body.string())
    }

    @Test
    fun testMangaPlusSimulation() {
        // MANGA Plus encodes hex key in URL fragment, e.g. #0102030405
        val imageBytes = ByteArray(1024) { (it % 256).toByte() }
        val hexKey = "deadbeefcafe"
        val key = hexKey.decodeHex()
        val obfuscatedBytes = imageBytes.xor(key)

        val client = OkHttpClient.Builder()
            .addInterceptor(XorInterceptor { it.url.fragment?.decodeHex() })
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(obfuscatedBytes.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                },
            )
            .build()

        val request = Request.Builder()
            .url("https://jumpg-webapi.tokyo-cdn.com/api/page#$hexKey")
            .build()

        val response = client.newCall(request).execute()
        assertArrayEquals(imageBytes, response.body.bytes())
    }

    @Test
    fun testXorInterceptorStaticKeyWithPredicate() {
        val plainText = "Azuki payload encrypted with single byte"
        val key = 174.toByte()
        val encrypted = plainText.toByteArray().xor(key)

        val client = OkHttpClient.Builder()
            .addInterceptor(XorInterceptor(key) { "drm" in it.url.queryParameterNames })
            .addInterceptor(
                Interceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(encrypted.toResponseBody("image/jpeg".toMediaType()))
                        .build()
                },
            )
            .build()

        val matchingRequest = Request.Builder()
            .url("https://example.com/page.jpg?drm=1")
            .build()
        val matchingResponse = client.newCall(matchingRequest).execute()
        assertEquals(plainText, matchingResponse.body.string())

        val nonMatchingRequest = Request.Builder()
            .url("https://example.com/page.jpg")
            .build()
        val nonMatchingResponse = client.newCall(nonMatchingRequest).execute()
        assertArrayEquals(encrypted, nonMatchingResponse.body.bytes())
    }
}
