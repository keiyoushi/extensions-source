package keiyoushi.zip

import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RemoteZipTest {

    private var archive = ByteArray(0)
    private var honourRange = true
    private val requests = AtomicInteger()

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            requests.incrementAndGet()
            val range = exchange.requestHeaders.getFirst("Range")?.removePrefix("bytes=")
            if (honourRange && range != null) {
                val (from, to) = range.split('-')
                val first = if (from.isEmpty()) maxOf(0, archive.size - to.toInt()) else from.toInt()
                val last = if (from.isEmpty() || to.isEmpty()) archive.size - 1 else minOf(to.toInt(), archive.size - 1)
                exchange.responseHeaders.add("Content-Range", "bytes $first-$last/${archive.size}")
                exchange.sendResponseHeaders(206, (last - first + 1).toLong())
                exchange.responseBody.use { it.write(archive, first, last - first + 1) }
            } else {
                exchange.sendResponseHeaders(200, archive.size.toLong())
                exchange.responseBody.use { it.write(archive) }
            }
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}/trial.zip"
    private val client = OkHttpClient()

    @After
    fun stop() = server.stop(0)

    @Test
    fun readsTheDirectoryFromTheTailAndAnEntryFromItsRange() {
        archive = zip(listOf("001.jpg" to "first page", "002.jpg" to "second page"))
        val entry = directory().entries.first { it.name == "002.jpg" }
        assertEquals("second page", read(entry))
        assertEquals(2, requests.get())
    }

    @Test
    fun fetchesADirectoryLargerThanTheTail() {
        archive = zip((1..1500).map { "a-long-folder-name-for-a-big-directory/${"%04d".format(it)}.jpg" to "" })
        assertEquals(1500, directory().entries.size)
        assertEquals(2, requests.get())
    }

    @Test
    fun readsAnEntryWithANameLongerThanTheDefaultAllowance() {
        archive = zip(listOf("体験版".repeat(80) + "/001.jpg" to "first page"))
        assertEquals("first page", read(directory().entries.single()))
    }

    @Test
    fun refusesAResponseThatIgnoredTheRange() {
        archive = zip(listOf("001.jpg" to "first page"))
        honourRange = false
        assertThrows(IOException::class.java) { request(0L..10).rangeSource() }
    }

    // Reads the directory the way zipDirectory does: the tail, then the directory if the tail doesn't hold it
    private fun directory(): ZipDirectory {
        val response = request(-MAX_EOCD_SEARCH.toLong()..-1)
        val tail = response.rangeSource().use { it.readByteArray() }
        val location = locateCentralDirectory(tail, response.header("Content-Range")!!.substringAfterLast('/').toLong())
        val cd = location.bytes ?: request(location.range).rangeSource().use { it.readByteArray() }
        return location.parse(cd, Charsets.UTF_8)
    }

    private fun read(entry: Entry): String = readEntry(request(entry.dataRange).rangeSource(), entry.compressedSize, entry.method).buffer().readUtf8()

    private fun request(range: LongRange): Response {
        val bytes = if (range.first < 0) "bytes=${range.first}" else "bytes=${range.first}-${range.last}"
        return client.newCall(Request.Builder().url(url).header("Range", bytes).build()).execute()
    }

    private fun zip(files: List<Pair<String, String>>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
