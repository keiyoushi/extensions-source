package eu.kanade.tachiyomi.multisrc.zingtheme

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ImageContentTypeTest {
    private fun response(path: String, type: String, bytes: ByteArray) = Response.Builder()
        .request(Request.Builder().url("https://cdn.example/$path").build())
        .protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .header("Content-Type", type)
        .body(bytes.toResponseBody(type.toMediaType())).build()

    @Test
    fun mislabeledJpegKeepsItsBytesAndGainsAnImageType() {
        val bytes = byteArrayOf(-1, -40, -1, 0, 1, 2, 3)
        imageContentType(response("page.JPG", "application/octet-stream", bytes)).use {
            assertEquals("image/jpeg", it.header("Content-Type"))
            assertEquals("image/jpeg", it.body.contentType().toString())
            assertArrayEquals(bytes, it.body.bytes())
        }
    }

    @Test
    fun anAlreadyCorrectImageTypeIsPreserved() {
        val original = response("page.jpg", "image/jpeg", byteArrayOf(1, 2))
        imageContentType(original).use { assertSame(original, it) }
    }

    @Test
    fun anUnknownExtensionIsNotMisidentified() {
        val original = response("download.bin", "application/octet-stream", byteArrayOf(1, 2))
        imageContentType(original).use { assertSame(original, it) }
    }
}
