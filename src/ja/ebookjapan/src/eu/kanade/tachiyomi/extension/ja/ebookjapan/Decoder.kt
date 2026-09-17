package eu.kanade.tachiyomi.extension.ja.ebookjapan

import android.util.Base64
import keiyoushi.utils.inflate
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class Book(
    private val fileId: String,
    private val prefix: String,
    private val margin: Int,
    private val unit: Int,
    private val generated: Boolean,
    private val tables: List<ByteArray>,
    private val pages: List<BookPage>,
) {
    val pageCount get() = pages.size

    // WASM func 184 (get_page_name)
    fun pageName(index: Int): String {
        val hash = "nf:$fileId/${index}_ebj".sha256().toHexString()
        return "${fileId.take(2)}/$fileId/$prefix/$hash.webp"
    }

    fun scramble(index: Int): Scramble {
        val page = pages[index]
        return Scramble(page.width, page.height, margin, unit, generated, tables[page.table])
    }
}

class BookPage(
    val width: Int,
    val height: Int,
    val table: Int,
)

// WASM func 164 (decrypt_session)
fun decryptBook(
    sessionId: String,
    code: String,
    openPayload: String,
    drmPayload: String,
    fileId: String,
): Book {
    val digests = sessionId.sha256() + code.sha256()
    val stride = STRIDES[digests.sumOf { it.toInt() and 0xFF } and 7]
    val derived = ByteArray(48)
    var offset = 0
    for (i in derived.indices) {
        derived[i] = digests[offset % digests.size]
        offset += stride
    }

    val stage = decryptGcm(derived, Base64.decode(openPayload, Base64.DEFAULT))
    val blob = decryptGcm(stage, Base64.decode(drmPayload, Base64.DEFAULT))
    return parseBook(blob.inflate(), fileId)
}

private fun decryptGcm(keyAndIv: ByteArray, data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyAndIv, 0, 32, "AES"),
            GCMParameterSpec(128, keyAndIv, 32, 16),
        )
    }
    return cipher.doFinal(data)
}

// used by WASM func 164
//  u8 flags, u16 pageCount
//  pageCount x { u16 number, u16 width, u16 height, u8, u8 table, u16, u8 jumps, jumps x 10 bytes }
//  u16 chapterCount, chapterCount x { NUL terminated name, u16 page }
//  NUL terminated path prefix
//  u8 margin, u8 unit, u8 tableCount, tableCount x table
//
// bit 0x20 of the flags picks how the tables are stored:
// - set for newer books, ten bytes seeding a generated permutation of 'unit' sized tiles
// - clear for older books, a plain 'unit * unit' table
private fun parseBook(data: ByteArray, fileId: String): Book {
    val buffer = ByteBuffer.wrap(data)
    val generated = buffer.get().toInt() and 0x20 != 0

    val pages = List(buffer.readUShort()) {
        buffer.skip(2) // page number
        val width = buffer.readUShort()
        val height = buffer.readUShort()
        buffer.skip(1)
        val table = buffer.readUByte()
        buffer.skip(2)
        buffer.skip(buffer.readUByte() * 10) // jump areas
        BookPage(width, height, table)
    }

    repeat(buffer.readUShort()) {
        buffer.readString() // chapter name
        buffer.skip(2)
    }

    val prefix = buffer.readString()
    val margin = buffer.readUByte()
    val unit = buffer.readUByte()
    val tableSize = if (generated) GENERATED_TABLE_SIZE else unit * unit
    val tables = List(buffer.readUByte()) {
        ByteArray(tableSize).also(buffer::get)
    }

    return Book(fileId, prefix, margin, unit, generated, tables, pages)
}

private fun String.sha256() = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())

private fun ByteBuffer.readUByte() = get().toInt() and 0xFF

private fun ByteBuffer.readUShort() = short.toInt() and 0xFFFF

private fun ByteBuffer.skip(count: Int) = position(position() + count)

private fun ByteBuffer.readString(): String = buildString {
    var byte = get().toInt()
    while (byte != 0) {
        append(byte.toChar())
        byte = get().toInt()
    }
}

private const val GENERATED_TABLE_SIZE = 10
private val STRIDES = intArrayOf(61, 211, 29, 197, 43, 179, 89, 79)
