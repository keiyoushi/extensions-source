package keiyoushi.network

import okio.Buffer
import okio.DeflaterSink
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.Deflater

class DeflateTest {

    @Test
    fun decodesZlibAndRawDeflate() {
        val text = "deflate body ".repeat(20)
        for (nowrap in listOf(false, true)) {
            val compressed = Buffer()
            DeflaterSink(compressed, Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)).buffer().use { it.writeUtf8(text) }
            assertEquals(text, Deflate.decompress(compressed).buffer().readUtf8())
        }
    }

    @Test
    fun emptyBody() = assertEquals("", Deflate.decompress(Buffer()).buffer().readUtf8())
}
