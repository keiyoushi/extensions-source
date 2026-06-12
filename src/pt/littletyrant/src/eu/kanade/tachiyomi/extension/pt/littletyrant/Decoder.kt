package eu.kanade.tachiyomi.extension.pt.littletyrant

import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import org.jsoup.nodes.Document
import java.io.InputStream

class Decoder {
    fun decrypt(response: Response, key: String): Response {
        val body = response.body ?: return response
        val stream = body.byteStream()
        val keyBytes = key.toByteArray()

        val decryptedStream = object : InputStream() {
            private var totalBytesRead = 0

            override fun read(): Int {
                val b = stream.read()
                if (b == -1) return -1
                if (totalBytesRead < XOR_LIMIT) {
                    val decrypted = b xor keyBytes[totalBytesRead % keyBytes.size].toInt()
                    totalBytesRead++
                    return decrypted and BYTE_MASK
                }
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = stream.read(b, off, len)
                if (n == -1) return -1
                val limit = minOf(n, XOR_LIMIT - totalBytesRead)
                for (i in 0 until limit) {
                    val idx = off + i
                    b[idx] = (b[idx].toInt() xor keyBytes[totalBytesRead % keyBytes.size].toInt()).toByte()
                    totalBytesRead++
                }
                return n
            }

            override fun close() {
                stream.close()
            }
        }

        val contentType = body.contentType()
        val contentLength = body.contentLength()
        val decryptedBody = decryptedStream.source().buffer().asResponseBody(contentType, contentLength)

        return response.newBuilder()
            .body(decryptedBody)
            .build()
    }

    fun extractPaths(document: Document): List<String> {
        val urlScript = document.selectFirst("script:containsData(_proxyUrls)")?.data()
            ?: error("No image URLS")

        val match = PROXY_URLS_REGEX.find(urlScript) ?: error("Unable to parse _proxyUrls")

        return match.groupValues[1]
            .split(",")
            .map { it.trim().trim('"').trim('\'').replace("\\/", "/") }
            .filter { it.isNotEmpty() }
    }

    companion object {
        private const val XOR_LIMIT = 1024
        private const val BYTE_MASK = 0xFF
        private val PROXY_URLS_REGEX = Regex("""_proxyUrls\s*=\s*\[(.*?)\]""", RegexOption.DOT_MATCHES_ALL)
    }
}
