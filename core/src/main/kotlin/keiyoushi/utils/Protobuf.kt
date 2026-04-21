package keiyoushi.utils

import android.util.Base64
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
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
 */
inline fun <reified T> ByteArray.decodeProto(): T = protoInstance.decodeFromByteArray<T>(this)

/**
 * Encodes the object into a [ByteArray] using Protobuf serialization.
 */
inline fun <reified T : Any> T.encodeProto(): ByteArray = protoInstance.encodeToByteArray(this)

/**
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 */
inline fun <reified T> Response.parseAsProto(): T = use { protoInstance.decodeFromByteArray(it.body.bytes()) }

/**
 * Parses the response body into an object of type [T] using Protobuf deserialization.
 *
 * The response is automatically closed after reading.
 *
 * @param transform A function to transform the raw [ByteArray] before it is decoded.
 */
inline fun <reified T> Response.parseAsProto(transform: (ByteArray) -> ByteArray): T = use { transform(it.body.bytes()).decodeProto() }

/**
 * Parses this [ResponseBody] into an object of type [T] using Protobuf deserialization.
 *
 * The body is automatically closed after reading.
 */
inline fun <reified T> ResponseBody.parseAsProto(): T = protoInstance.decodeFromByteArray(bytes())

/**
 * Encodes the object into a Protobuf [RequestBody] with the [PROTOBUF_MEDIA_TYPE] content type.
 */
inline fun <reified T : Any> T.toRequestBodyProto(): RequestBody = encodeProto().toRequestBody(PROTOBUF_MEDIA_TYPE)

/**
 * Decodes a Base64-encoded string into an object of type [T] using Protobuf deserialization.
 *
 * The string is expected to be encoded with [Base64.NO_WRAP].
 */
inline fun <reified T> String.decodeProtoBase64(): T = protoInstance.decodeFromByteArray(Base64.decode(this, Base64.NO_WRAP))

/**
 * Encodes the object to a Protobuf [ByteArray] and returns it as a Base64-encoded string.
 *
 * The resulting string is encoded with [Base64.NO_WRAP].
 */
inline fun <reified T : Any> T.encodeProtoBase64(): String = Base64.encodeToString(encodeProto(), Base64.NO_WRAP)
