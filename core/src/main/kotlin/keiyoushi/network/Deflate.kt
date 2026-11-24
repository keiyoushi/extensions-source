package keiyoushi.network

import okhttp3.CompressionInterceptor
import okio.BufferedSource
import okio.InflaterSource
import okio.Source
import java.util.zip.Inflater

/**
 * `Content-Encoding: deflate`, which browsers offer next to gzip, brotli and zstd. Decodes the zlib
 * stream the spec defines and the raw deflate some servers send.
 */
internal object Deflate : CompressionInterceptor.DecompressionAlgorithm {
    override val encoding = "deflate"

    // An empty body stays empty, this decoder sits on every request
    override fun decompress(compressedSource: BufferedSource): Source = if (compressedSource.exhausted()) {
        compressedSource
    } else {
        InflaterSource(compressedSource, Inflater(!compressedSource.startsWithZlibHeader()))
    }

    // RFC 1950: deflate method in the low CMF bits, and CMF/FLG a multiple of 31
    private fun BufferedSource.startsWithZlibHeader(): Boolean {
        if (!request(2)) return false
        val cmf = buffer[0].toInt() and 0xFF
        val flg = buffer[1].toInt() and 0xFF
        return cmf and 0x0F == 8 && (cmf * 256 + flg) % 31 == 0
    }
}
