package keiyoushi.utils

import android.util.Base64
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

val protoInstance: ProtoBuf = Injekt.get()
val PROTOBUF_MEDIA_TYPE = "application/protobuf".toMediaType()

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
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 *
 * @param proto The [ProtoBuf] instance to use for deserialization.
 */
inline fun <reified T> Response.parseAsProto(proto: ProtoBuf = protoInstance): T = use { it.body.bytes().decodeProto(proto) }

/**
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 *
 * @param proto The [ProtoBuf] instance to use for deserialization.
 * @param transform A function to transform the raw [ByteArray] before it is decoded.
 */
inline fun <reified T> Response.parseAsProto(proto: ProtoBuf = protoInstance, transform: (ByteArray) -> ByteArray): T = use { transform(it.body.bytes()).decodeProto(proto) }

/**
 * Parses this [ResponseBody] into an object of type [T] using Protobuf deserialization.
 *
 * The body is automatically closed after reading.
 *
 * @param proto The [ProtoBuf] instance to use for deserialization.
 */
inline fun <reified T> ResponseBody.parseAsProto(proto: ProtoBuf = protoInstance): T = bytes().decodeProto(proto)

/**
 * Encodes the object into a Protobuf [RequestBody] with the given [mediaType].
 *
 * @param proto The [ProtoBuf] instance to use for serialization.
 * @param mediaType The [MediaType] to use for the request body. Defaults to [PROTOBUF_MEDIA_TYPE] (`application/protobuf`).
 */
inline fun <reified T : Any> T.toRequestBodyProto(proto: ProtoBuf = protoInstance, mediaType: MediaType = PROTOBUF_MEDIA_TYPE): RequestBody = encodeProto(proto).toRequestBody(mediaType)

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
