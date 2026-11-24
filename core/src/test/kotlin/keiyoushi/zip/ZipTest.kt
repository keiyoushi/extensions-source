package keiyoushi.zip

import keiyoushi.utils.xor
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipOutputStream

class ZipTest {

    private val ms932 = Charset.forName("MS932")

    // Entry names

    @Test
    fun decodesUnflaggedNamesWithTheGivenCharset() {
        val archive = zip("上/001.jpg" to "", "下/001.jpg" to "", charset = ms932)
        assertEquals(listOf("上/001.jpg", "下/001.jpg"), directoryOf(archive, ms932).entries.map { it.name })
    }

    @Test
    fun readsFlaggedNamesAsUtf8WhateverTheCharset() {
        val archive = zip("体験版/001.jpg" to "")
        assertEquals("体験版/001.jpg", directoryOf(archive, ms932).entries.single().name)
    }

    @Test
    fun decodesUnflaggedNamesAsUtf8ByDefault() {
        assertEquals("体験版/001.jpg", parseCentralDirectory(header("体験版/001.jpg".toByteArray())).single().name)
    }

    @Test
    fun prefersAMatchingUnicodePathField() {
        val stored = "上/001.jpg".toByteArray(ms932)
        val entry = parseCentralDirectory(header(stored, extra = unicodePath(stored, "上/001.jpg")), Charsets.ISO_8859_1).single()
        assertEquals("上/001.jpg", entry.name)
    }

    @Test
    fun ignoresAStaleUnicodePathField() {
        val stored = "下/001.jpg".toByteArray(ms932)
        val stale = unicodePath("上/001.jpg".toByteArray(ms932), "上/001.jpg")
        assertEquals("下/001.jpg", parseCentralDirectory(header(stored, extra = stale), ms932).single().name)
    }

    @Test
    fun ignoresAUnicodePathFieldOfAnotherVersion() {
        val stored = "下/001.jpg".toByteArray(ms932)
        val field = unicodePath(stored, "上/001.jpg", version = 2)
        assertEquals("下/001.jpg", parseCentralDirectory(header(stored, extra = field), ms932).single().name)
    }

    @Test
    fun ignoresAFieldThatRunsPastTheExtraBlock() {
        val stored = "上/001.jpg".toByteArray(ms932)
        val field = unicodePath(stored, "上/001.jpg")
        val truncated = field.copyOf(field.size - 3)
        val entry = parseCentralDirectory(header(stored, extra = truncated), Charsets.ISO_8859_1).single()
        assertEquals(String(stored, Charsets.ISO_8859_1), entry.name)
    }

    // ZIP64

    @Test
    fun keepsZip64OffsetsNextToOtherExtraFields() {
        val stored = "a.jpg".toByteArray()
        val zip64 = Buffer().writeShortLe(0x0001).writeShortLe(8).writeLongLe(0x1_0000_0000L).readByteArray()
        val entry = parseCentralDirectory(header(stored, extra = unicodePath(stored, "a.jpg") + zip64, localHeaderOffset = 0xFFFFFFFFL)).single()
        assertEquals(0x1_0000_0000L, entry.localHeaderOffset)
    }

    @Test
    fun readsZip64SizesInTheirFieldOrder() {
        val zip64 = Buffer().writeShortLe(0x0001).writeShortLe(16).writeLongLe(0x2_0000_0000L).writeLongLe(0x1_0000_0000L).readByteArray()
        val entry = parseCentralDirectory(header("a.jpg".toByteArray(), extra = zip64, sizes = 0xFFFFFFFFL)).single()
        assertEquals(0x1_0000_0000L, entry.compressedSize)
    }

    @Test
    fun readsTheZip64EndOfCentralDirectory() {
        val archive = toZip64(zip("001.jpg" to "first page"))
        val entry = directoryOf(archive).entries.single()
        assertEquals("001.jpg", entry.name)
        assertEquals("first page", read(archive, entry))
    }

    @Test
    fun findsTheZip64RecordByItsSizeNotTheFirstSignature() {
        // Extensible data long enough to hold a decoy signature where a backwards scan meets it first
        val decoy = Buffer().writeIntLe(0x06064b50).write(ByteArray(76)).readByteArray()
        val archive = toZip64(zip("001.jpg" to "first page"), extensibleData = decoy)
        assertEquals("first page", read(archive, directoryOf(archive).entries.single()))
    }

    @Test
    fun failsWhenTheZip64RecordIsMissing() {
        val zip64 = toZip64(zip("001.jpg" to ""))
        val withoutRecord = zip64.copyOfRange(zip64.size - 22, zip64.size)
        assertThrows(ZipException::class.java) { findEocd(withoutRecord) }
    }

    @Test
    fun failsWithoutAnEndRecord() {
        assertThrows(ZipException::class.java) { findEocd(ByteArray(100)) }
    }

    // Locating the central directory

    @Test
    fun skipsACommentThatLooksLikeTheEndRecord() {
        val comment = "PK\u0005\u0006" + "\u0000".repeat(18) + "end"
        val archive = zip("001.jpg" to "first page", comment = comment)
        assertEquals("first page", read(archive, directoryOf(archive).entries.single()))
    }

    @Test
    fun resolvesAbsoluteOffsetsAfterPrependedData() {
        val zip = zip("001.jpg" to "first page")
        val archive = ByteArray(100) { 1 } + zip
        val entry = directoryOf(archive).entries.single()
        assertEquals(100 + directoryOf(zip).entries.single().localHeaderOffset, entry.localHeaderOffset)
        assertEquals("first page", read(archive, entry))
    }

    @Test
    fun locatesADirectoryOutsideTheTail() {
        val archive = zip("001.jpg" to "first page", "002.jpg" to "second page")
        val tail = archive.copyOfRange(archive.size - 22, archive.size)
        val location = locateCentralDirectory(tail, archive.size.toLong())
        assertNull(location.bytes)
        val cd = archive.copyOfRange(location.range.first.toInt(), location.range.last.toInt() + 1)
        assertEquals(listOf("001.jpg", "002.jpg"), location.parse(cd, Charsets.UTF_8).entries.map { it.name })
    }

    @Test
    fun failsOnATruncatedCentralDirectory() {
        val cd = header("001.jpg".toByteArray())
        assertThrows(ZipException::class.java) { parseCentralDirectory(cd.copyOf(cd.size - 2)) }
    }

    // Reading entries

    @Test
    fun allowsForTheNameAndExtraFieldAheadOfTheData() {
        val archive = zip("体験版".repeat(80) + "/001.jpg" to "first page", localExtra = ByteArray(10))
        val entry = directoryOf(archive).entries.single()
        assertEquals(30 + 728 + 10 + MAX_LOCAL_FILE_HEADER, entry.localHeaderAllowance)
        assertEquals("first page", read(archive, entry))
    }

    @Test
    fun readsADeflatedEntryPastItsLocalHeader() {
        val archive = zip("a/001.txt" to "first page", localExtra = ByteArray(6))
        assertEquals("first page", read(archive, directoryOf(archive).entries.single()))
    }

    @Test
    fun readsAStoredEntry() {
        val archive = zip("001.txt" to "first page", stored = true)
        val entry = directoryOf(archive).entries.single()
        assertEquals(0, entry.method)
        assertEquals("first page", read(archive, entry))
    }

    @Test
    fun decodesThePayloadBeforeInflating() {
        val key = byteArrayOf(0x5a)
        val archive = zip("001.txt" to "first page")
        val entry = directoryOf(archive).entries.single()
        val dataStart = entry.localHeaderOffset.toInt() + 30 + "001.txt".length
        for (i in dataStart until dataStart + entry.compressedSize.toInt()) archive[i] = (archive[i].toInt() xor 0x5a).toByte()
        assertEquals("first page", read(archive, entry) { it.xor(key) })
    }

    @Test
    fun rejectsAnUnsupportedMethod() {
        assertThrows(ZipException::class.java) { readEntry(Buffer(), 0, method = 12) }
    }

    @Test
    fun rejectsAnEncryptedEntry() {
        val archive = zip("001.txt" to "first page")
        val entry = directoryOf(archive).entries.single()
        archive[entry.localHeaderOffset.toInt() + 6] = 1
        assertThrows(ZipException::class.java) { read(archive, entry) }
    }

    @Test
    fun rejectsDataThatIsNotALocalHeader() {
        assertThrows(ZipException::class.java) { readEntry(Buffer().write(ByteArray(64)), 0, method = 0) }
    }

    @Test
    fun fixedLengthStopsAtItsLength() {
        assertEquals("ab", Buffer().writeUtf8("abc").fixedLength(2).buffer().readUtf8())
    }

    @Test
    fun fixedLengthFailsWhenTheSourceEndsEarly() {
        assertThrows(EOFException::class.java) { Buffer().writeUtf8("abc").fixedLength(5).buffer().readUtf8() }
    }

    // Builders

    private fun zip(
        vararg files: Pair<String, String>,
        charset: Charset = Charsets.UTF_8,
        comment: String? = null,
        stored: Boolean = false,
        localExtra: ByteArray? = null,
    ): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out, charset).use { zip ->
            comment?.let(zip::setComment)
            files.forEach { (name, text) ->
                val data = text.toByteArray()
                zip.putNextEntry(
                    ZipEntry(name).apply {
                        extra = localExtra
                        if (stored) {
                            method = ZipEntry.STORED
                            size = data.size.toLong()
                            crc = CRC32().apply { update(data) }.value
                        }
                    },
                )
                zip.write(data)
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun directoryOf(archive: ByteArray, charset: Charset = Charsets.UTF_8): ZipDirectory {
        val location = locateCentralDirectory(archive, archive.size.toLong())
        return location.parse(location.bytes!!, charset)
    }

    // Reads the entry from the byte range a range request would fetch
    private fun read(archive: ByteArray, entry: Entry, decode: (BufferedSource) -> Source = { it }): String {
        val range = entry.dataRange
        val end = minOf(range.last.toInt() + 1, archive.size)
        val source = Buffer().write(archive, range.first.toInt(), end - range.first.toInt())
        return readEntry(source, entry.compressedSize, entry.method, decode).buffer().readUtf8()
    }

    // Replaces the end record (an archive without a comment) with a ZIP64 end record, locator and a sentinel end record
    private fun toZip64(archive: ByteArray, extensibleData: ByteArray = ByteArray(0)): ByteArray {
        val end = Buffer().write(archive, archive.size - 22, 22)
        end.skip(10)
        val entries = end.readShortLe().toLong()
        val cdSize = end.readIntLe().toLong()
        val cdOffset = end.readIntLe().toLong()
        val zip64EndOffset = archive.size - 22L
        val records = Buffer()
            .writeIntLe(0x06064b50).writeLongLe(44L + extensibleData.size).writeShortLe(45).writeShortLe(45).writeIntLe(0).writeIntLe(0)
            .writeLongLe(entries).writeLongLe(entries).writeLongLe(cdSize).writeLongLe(cdOffset).write(extensibleData)
            .writeIntLe(0x07064b50).writeIntLe(0).writeLongLe(zip64EndOffset).writeIntLe(1)
            .writeIntLe(0x06054b50).writeShortLe(0).writeShortLe(0).writeShortLe(0xFFFF).writeShortLe(0xFFFF)
            .writeIntLe(-1).writeIntLe(-1).writeShortLe(0)
            .readByteArray()
        return archive.copyOf(archive.size - 22) + records
    }

    private fun unicodePath(stored: ByteArray, name: String, version: Int = 1): ByteArray {
        val utf8 = name.toByteArray()
        val crc = CRC32().apply { update(stored) }.value
        return Buffer().writeShortLe(0x7075).writeShortLe(5 + utf8.size).writeByte(version).writeIntLe(crc.toInt()).write(utf8).readByteArray()
    }

    private fun header(name: ByteArray, extra: ByteArray = ByteArray(0), localHeaderOffset: Long = 0, sizes: Long = 0): ByteArray = Buffer()
        .writeIntLe(0x02014b50)
        .writeShortLe(20) // version made by
        .writeShortLe(20) // version needed
        .writeShortLe(0) // flags
        .writeShortLe(0) // method
        .writeIntLe(0) // time and date
        .writeIntLe(0) // crc
        .writeIntLe(sizes.toInt()) // compressed size
        .writeIntLe(sizes.toInt()) // uncompressed size
        .writeShortLe(name.size)
        .writeShortLe(extra.size)
        .writeShortLe(0) // comment length
        .writeShortLe(0) // disk
        .writeShortLe(0) // internal attributes
        .writeIntLe(0) // external attributes
        .writeIntLe(localHeaderOffset.toInt())
        .write(name)
        .write(extra)
        .readByteArray()
}
