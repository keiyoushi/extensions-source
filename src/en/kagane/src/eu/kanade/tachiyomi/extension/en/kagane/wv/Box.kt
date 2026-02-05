package eu.kanade.tachiyomi.extension.en.kagane.wv

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

sealed class BoxType

@Suppress("unused")
class ProtectionSystemHeaderBox(
    val type: String,
    val version: Int,
    val flags: Int,
    val systemID: UUID,
    val keyIDs: List<UUID>?,
    val initData: ByteArray,
) : BoxType()

class RawBox(
    val type: String,
    val data: ByteArray,
) : BoxType()

@Suppress("unused")
class Pssh(
    val offset: Int,
    val type: String,
    val content: BoxType,
    val end: Int,
)

fun ByteArray.toUUID(): UUID {
    require(size == 16) { "UUID must be 16 bytes, got $size instead" }

    return ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).let {
        UUID(it.long, it.long)
    }
}

fun parsePssh(data: ByteArray): Pssh {
    val buffer = ByteBuffer.wrap(data)
    val offset = 0

    @Suppress("UNUSED_VARIABLE", "unused")
    val size = buffer.int

    val typeByteArr = ByteArray(4)
    buffer.get(typeByteArr)
    val type = String(typeByteArr)

    val content = when (type) {
        "pssh" -> parsePsshBox(type, buffer)

        else -> {
            val remainingSize = buffer.remaining()
            val remainingArr = ByteArray(remainingSize)
            buffer.get(remainingArr)

            RawBox(type, remainingArr)
        }
    }

    return Pssh(offset, type, content, buffer.position())
}

private fun parsePsshBox(type: String, stream: ByteBuffer): ProtectionSystemHeaderBox {
    val version = stream.get().toInt()

    val flagArr = ByteArray(3)
    stream.get(flagArr)
    val flags = ((flagArr[0].toInt() and 0xFF) shl 16) or
        ((flagArr[1].toInt() and 0xFF) shl 8) or
        (flagArr[2].toInt() and 0xFF)

    val systemIDArr = ByteArray(16)
    stream.get(systemIDArr)
    val systemID = systemIDArr.toUUID()

    val keyIDs = if (version == 1) {
        List(stream.int) {
            val arr = ByteArray(16)
            stream.get(arr)
            arr.toUUID()
        }
    } else {
        null
    }

    val initDataSize = stream.int
    val initDataArr = ByteArray(initDataSize)
    stream.get(initDataArr)

    return ProtectionSystemHeaderBox(type, version, flags, systemID, keyIDs, initDataArr)
}
