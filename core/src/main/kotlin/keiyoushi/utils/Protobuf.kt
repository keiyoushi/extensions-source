package keiyoushi.utils

import android.util.Base64
import keiyoushi.utils.protobuf.ProtobufSinkEncoder
import keiyoushi.utils.protobuf.ProtobufSinkWriter
import keiyoushi.utils.protobuf.ProtobufSourceDecoder
import keiyoushi.utils.protobuf.ProtobufSourceReader
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.BufferedSink
import okio.BufferedSource
import okio.Source
import okio.buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val protoInstance: ProtoBuf = Injekt.get()
val PROTOBUF_MEDIA_TYPE = "application/protobuf".toMediaType()

@PublishedApi
internal fun <T> BufferedSource.decodeProto(strategy: DeserializationStrategy<T>, byteCount: Long): T = ProtobufSourceDecoder(ProtobufSourceReader(this, byteCount), strategy.descriptor, pendingRoot = true).decodeSerializableValue(strategy)

@PublishedApi
internal fun <T> BufferedSink.encodeProto(strategy: SerializationStrategy<T>, value: T, encodeDefaults: Boolean) = ProtobufSinkEncoder(ProtobufSinkWriter(this), strategy.descriptor, encodeDefaults, pendingRoot = true).encodeSerializableValue(strategy, value)

/**
 * Decodes a [ByteArray] into an object of type [T] using Protobuf deserialization.
 *
 * @param proto The [ProtoBuf] instance to use for deserialization.
 */
inline fun <reified T> ByteArray.decodeProto(proto: ProtoBuf = protoInstance): T = proto.decodeFromByteArray<T>(this)

/**
 * Encodes the object into a [ByteArray] using Protobuf serialization.
 *
 * @param proto The [ProtoBuf] instance to use for serialization.
 */
inline fun <reified T : Any> T.encodeProto(proto: ProtoBuf = protoInstance): ByteArray = proto.encodeToByteArray(this)

/**
 * Decodes this [BufferedSource] into an object of type [T] using Protobuf deserialization.
 *
 * @param byteCount Limits decoding to the first [byteCount] bytes. Defaults to `-1`, meaning the message ends where the source does.
 */
inline fun <reified T> BufferedSource.decodeProto(byteCount: Long = -1L): T = decodeProto(serializer<T>(), byteCount)

/**
 * Encodes [value] into this [BufferedSink] using Protobuf serialization.
 *
 * @param encodeDefaults Whether properties still holding their default value are encoded.
 */
inline fun <reified T> BufferedSink.encodeProto(value: T, encodeDefaults: Boolean = false) = encodeProto(serializer<T>(), value, encodeDefaults)

/**
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 */
inline fun <reified T> Response.parseAsProto(): T = use { it.body.source().decodeProto<T>() }

/**
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 *
 * @param transform A function to transform the raw [BufferedSource] before it is decoded.
 */
inline fun <reified T> Response.parseAsProto(transform: (BufferedSource) -> Source): T = use { transform(it.body.source()).buffer().decodeProto<T>() }

/**
 * Parses this [ResponseBody] into an object of type [T] using Protobuf deserialization.
 *
 * The body is automatically closed after reading.
 */
inline fun <reified T> ResponseBody.parseAsProto(): T = use { it.source().decodeProto<T>() }

/**
 * Encodes the object into a Protobuf [RequestBody] with the given [mediaType].
 *
 * The body is encoded directly into the request sink, so [RequestBody.contentLength] returns `-1` and it is sent chunked.
 *
 * @param mediaType The [MediaType] to use for the request body. Defaults to [PROTOBUF_MEDIA_TYPE] (`application/protobuf`).
 * @param encodeDefaults Whether properties still holding their default value are encoded.
 */
inline fun <reified T : Any> T.toRequestBodyProto(mediaType: MediaType = PROTOBUF_MEDIA_TYPE, encodeDefaults: Boolean = false): RequestBody {
    val value = this
    val strategy = serializer<T>()
    return object : RequestBody() {
        override fun contentType(): MediaType = mediaType
        override fun contentLength(): Long = -1
        override fun writeTo(sink: BufferedSink) = sink.encodeProto(strategy, value, encodeDefaults)
    }
}

/**
 * Decodes a Base64-encoded string into an object of type [T] using Protobuf deserialization.
 *
 * The string is expected to be encoded with [Base64.NO_WRAP].
 *
 * @param proto The [ProtoBuf] instance to use for deserialization.
 */
inline fun <reified T> String.decodeProtoBase64(proto: ProtoBuf = protoInstance): T = Base64.decode(this, Base64.NO_WRAP).decodeProto(proto)

/**
 * Encodes the object to a Protobuf [ByteArray] and returns it as a Base64-encoded string.
 *
 * The resulting string is encoded with [Base64.NO_WRAP].
 *
 * @param proto The [ProtoBuf] instance to use for serialization.
 */
inline fun <reified T : Any> T.encodeProtoBase64(proto: ProtoBuf = protoInstance): String = Base64.encodeToString(encodeProto(proto), Base64.NO_WRAP)
