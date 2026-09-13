package keiyoushi.utils.protobuf

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.protobuf.ProtoIntegerType
import kotlinx.serialization.protobuf.ProtoNumber
import kotlinx.serialization.protobuf.ProtoOneOf
import kotlinx.serialization.protobuf.ProtoPacked
import kotlinx.serialization.protobuf.ProtoType
import okio.Buffer
import okio.BufferedSink
import okio.utf8Size
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

// some parts are taken from here:
// https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/protobuf/commonMain/src/kotlinx/serialization/protobuf/internal/*
@PublishedApi
internal class ProtobufSinkWriter(private val root: BufferedSink) {
    private var sink: BufferedSink = root
    private var depth = 0
    private var buffers = arrayOfNulls<Buffer>(8)
    private val varintScratch = ByteArray(16)

    fun beginMessage() {
        if (depth == buffers.size) buffers = buffers.copyOf(depth * 2)
        val buffer = buffers[depth] ?: Buffer().also { buffers[depth] = it }
        depth++
        sink = buffer
    }

    fun endMessage(fieldNumber: Int, omitIfEmpty: Boolean = false) {
        val message = sink as Buffer
        depth--
        sink = if (depth == 0) root else buffers[depth - 1]!!
        val size = message.size
        if (omitIfEmpty && size == 0L) return
        val scratch = varintScratch
        var count = putVarint(scratch, 0, (fieldNumber.toLong() shl 3) or WIRE_SIZE_DELIMITED.toLong())
        count = putVarint(scratch, count, size)
        sink.write(scratch, 0, count)
        sink.write(message, size)
    }

    private fun writeTag(fieldNumber: Int, wireType: Int) = writeVarint((fieldNumber.toLong() shl 3) or wireType.toLong())

    private fun putVarint(target: ByteArray, offset: Int, value: Long): Int {
        var v = value
        var at = offset
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                target[at++] = v.toByte()
                return at
            }
            target[at++] = ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
    }

    fun writeVarint(value: Long) {
        if (value and 0x7FL.inv() == 0L) {
            sink.writeByte(value.toInt())
            return
        }
        sink.write(varintScratch, 0, putVarint(varintScratch, 0, value))
    }

    fun writeVarint(fieldNumber: Int, value: Long) {
        writeTag(fieldNumber, WIRE_VARINT)
        writeVarint(value)
    }

    fun writeFixed32(bits: Int) = sink.writeIntLe(bits)

    fun writeFixed32(fieldNumber: Int, bits: Int) {
        writeTag(fieldNumber, WIRE_I32)
        sink.writeIntLe(bits)
    }

    fun writeFixed64(bits: Long) = sink.writeLongLe(bits)

    fun writeFixed64(fieldNumber: Int, bits: Long) {
        writeTag(fieldNumber, WIRE_I64)
        sink.writeLongLe(bits)
    }

    fun writeLengthDelimitedUtf8(fieldNumber: Int, value: String) {
        writeTag(fieldNumber, WIRE_SIZE_DELIMITED)
        writeVarint(value.utf8Size())
        sink.writeUtf8(value)
    }

    fun writeLengthDelimited(fieldNumber: Int, bytes: ByteArray) {
        writeTag(fieldNumber, WIRE_SIZE_DELIMITED)
        writeVarint(bytes.size.toLong())
        sink.write(bytes)
    }
}

private class ProtoEncodeInfo(descriptor: SerialDescriptor) {
    val protoIds = IntArray(descriptor.elementsCount)
    val integerTypes = Array(descriptor.elementsCount) { ProtoIntegerType.DEFAULT }
    val packed = BooleanArray(descriptor.elementsCount)
    val oneOf = BooleanArray(descriptor.elementsCount)

    init {
        for (i in 0 until descriptor.elementsCount) {
            var id = i + 1
            val annotations = descriptor.getElementAnnotations(i)
            for (j in annotations.indices) {
                when (val annotation = annotations[j]) {
                    is ProtoNumber -> id = annotation.number
                    is ProtoType -> integerTypes[i] = annotation.type
                    is ProtoPacked -> packed[i] = true
                    is ProtoOneOf -> oneOf[i] = true
                }
            }
            protoIds[i] = id
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<SerialDescriptor, ProtoEncodeInfo>()

        fun of(descriptor: SerialDescriptor): ProtoEncodeInfo = cache.computeIfAbsent(descriptor, ::ProtoEncodeInfo)
    }
}

@PublishedApi
internal open class ProtobufSinkEncoder(
    protected val writer: ProtobufSinkWriter,
    descriptor: SerialDescriptor,
    protected val encodeDefaults: Boolean,
    private var pendingRoot: Boolean = false,
) : AbstractEncoder() {
    override val serializersModule: SerializersModule = EmptySerializersModule()
    override fun shouldEncodeElementDefault(descriptor: SerialDescriptor, index: Int): Boolean = encodeDefaults

    protected var currentFieldNumber: Int = -1
    protected var currentIntegerType: ProtoIntegerType = ProtoIntegerType.DEFAULT
    private var currentPacked = false
    private var pendingOneOf = false

    protected open val writesTags: Boolean get() = true

    private val encodeInfo = ProtoEncodeInfo.of(descriptor)

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        if (encodeInfo.oneOf[index]) {
            pendingOneOf = true
        } else {
            currentFieldNumber = encodeInfo.protoIds[index]
            currentIntegerType = encodeInfo.integerTypes[index]
            currentPacked = encodeInfo.packed[index]
        }
        return true
    }

    private fun writeIntValue(value: Int) {
        when (currentIntegerType) {
            ProtoIntegerType.DEFAULT -> writeVarintValue(value.toLong())
            ProtoIntegerType.SIGNED -> writeVarintValue(((value shl 1) xor (value shr 31)).toLong() and 0xFFFFFFFFL)
            ProtoIntegerType.FIXED -> if (writesTags) writer.writeFixed32(currentFieldNumber, value) else writer.writeFixed32(value)
        }
    }

    private fun writeLongValue(value: Long) {
        when (currentIntegerType) {
            ProtoIntegerType.DEFAULT -> writeVarintValue(value)
            ProtoIntegerType.SIGNED -> writeVarintValue((value shl 1) xor (value shr 63))
            ProtoIntegerType.FIXED -> if (writesTags) writer.writeFixed64(currentFieldNumber, value) else writer.writeFixed64(value)
        }
    }

    private fun writeVarintValue(value: Long) {
        if (writesTags) writer.writeVarint(currentFieldNumber, value) else writer.writeVarint(value)
    }

    override fun encodeBoolean(value: Boolean) = writeVarintValue(if (value) 1L else 0L)
    override fun encodeInt(value: Int) = writeIntValue(value)
    override fun encodeLong(value: Long) = writeLongValue(value)
    override fun encodeByte(value: Byte) = writeIntValue(value.toInt())
    override fun encodeShort(value: Short) = writeIntValue(value.toInt())
    override fun encodeChar(value: Char) = writeIntValue(value.code)
    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) = writeVarintValue(index.toLong())

    override fun encodeFloat(value: Float) {
        if (writesTags) writer.writeFixed32(currentFieldNumber, value.toRawBits()) else writer.writeFixed32(value.toRawBits())
    }

    override fun encodeDouble(value: Double) {
        if (writesTags) writer.writeFixed64(currentFieldNumber, value.toRawBits()) else writer.writeFixed64(value.toRawBits())
    }

    override fun encodeNull() = Unit

    override fun encodeString(value: String) {
        if (!writesTags) throw IOException("Strings cannot appear in a packed field")
        writer.writeLengthDelimitedUtf8(currentFieldNumber, value)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> encodeSerializableValue(serializer: SerializationStrategy<T>, value: T) {
        if (serializer === ByteArraySerializer()) {
            writer.writeLengthDelimited(currentFieldNumber, value as ByteArray)
        } else {
            super.encodeSerializableValue(serializer, value)
        }
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder = if (currentPacked && descriptor.getElementDescriptor(0).isPackedWhenWritten) {
        writer.beginMessage()
        PackedFieldEncoder(writer, currentFieldNumber, descriptor, encodeDefaults, currentIntegerType)
    } else {
        RepeatedFieldEncoder(writer, currentFieldNumber, descriptor, encodeDefaults, currentIntegerType)
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        if (pendingOneOf) {
            pendingOneOf = false
            return OneOfWrapperEncoder(writer, descriptor, encodeDefaults)
        }
        return when (descriptor.kind) {
            StructureKind.LIST -> beginCollection(descriptor, 0)
            StructureKind.CLASS, StructureKind.OBJECT -> if (pendingRoot) {
                pendingRoot = false
                this
            } else {
                writer.beginMessage()
                NestedMessageEncoder(writer, currentFieldNumber, descriptor, encodeDefaults)
            }
            else -> throw IOException("Unsupported structure kind ${descriptor.kind} in ${descriptor.serialName}")
        }
    }

    override fun endStructure(descriptor: SerialDescriptor) {}
}

private class NestedMessageEncoder(
    writer: ProtobufSinkWriter,
    private val parentFieldNumber: Int,
    descriptor: SerialDescriptor,
    encodeDefaults: Boolean,
) : ProtobufSinkEncoder(writer, descriptor, encodeDefaults) {
    override fun endStructure(descriptor: SerialDescriptor) {
        writer.endMessage(parentFieldNumber)
    }
}

private class RepeatedFieldEncoder(
    writer: ProtobufSinkWriter,
    private val fieldNumber: Int,
    descriptor: SerialDescriptor,
    encodeDefaults: Boolean,
    private val elementIntegerType: ProtoIntegerType,
) : ProtobufSinkEncoder(writer, descriptor, encodeDefaults) {
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentFieldNumber = fieldNumber
        currentIntegerType = elementIntegerType
        return true
    }
}

private class PackedFieldEncoder(
    writer: ProtobufSinkWriter,
    private val parentFieldNumber: Int,
    descriptor: SerialDescriptor,
    encodeDefaults: Boolean,
    private val elementIntegerType: ProtoIntegerType,
) : ProtobufSinkEncoder(writer, descriptor, encodeDefaults) {

    override val writesTags: Boolean get() = false

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        currentIntegerType = elementIntegerType
        return true
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        // an empty packed field is written as absent, the same as an empty repeated field
        writer.endMessage(parentFieldNumber, omitIfEmpty = true)
    }
}

private class OneOfWrapperEncoder(
    writer: ProtobufSinkWriter,
    descriptor: SerialDescriptor,
    encodeDefaults: Boolean,
) : ProtobufSinkEncoder(writer, descriptor, encodeDefaults) {
    // index 0 is the sealed class's name discriminator, protobuf oneof has no room for it on the wire
    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean = index != 0

    // the chosen variant's own field is written straight into the same writer, flattened
    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = ProtobufSinkEncoder(writer, descriptor, encodeDefaults)
}
