package keiyoushi.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.buffer
import okio.source
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.InputStream

val jsonInstance: Json = Injekt.get()

val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Parses JSON string into an object of type [T].
 */
inline fun <reified T> String.parseAs(json: Json = jsonInstance): T = json.decodeFromString(serializer(), this)

/**
 * Parses JSON string into an object of type [T], applying a [transform] function to the string before parsing.
 *
 * @param json The [Json] instance to use for deserialization.
 * @param transform A function to transform the original JSON string before it is parsed.
 */
inline fun <reified T> String.parseAs(json: Json = jsonInstance, transform: (String) -> String): T = transform(this).parseAs(json)

/**
 * Parses the response body into an object of type [T].
 */
inline fun <reified T> Response.parseAs(json: Json = jsonInstance): T = use {
    json.decodeFromBufferedSource(serializer(), it.body.source())
}

/**
 * Parses the response body into an object of type [T], applying a transformation to the raw JSON string before parsing.
 *
 * @param json The [Json] instance to use for parsing. Defaults to the injected instance.
 * @param transform A function to transform the JSON string before it's decoded.
 */
inline fun <reified T> Response.parseAs(json: Json = jsonInstance, transform: (String) -> String): T = use {
    it.body.string().parseAs(json, transform)
}

/**
 * Parses a [JsonElement] into an object of type [T].
 *
 * @param json The [Json] instance to use for parsing. Defaults to the injected instance.
 */
inline fun <reified T> JsonElement.parseAs(json: Json = jsonInstance): T = json.decodeFromJsonElement(serializer(), this)

/**
 * Parses a [InputStream] into an object of type [T]
 *
 * @param json The [Json] instance to use for parsing. Defaults to the injected instance.
 */
inline fun <reified T> InputStream.parseAs(json: Json = jsonInstance): T = use {
    json.decodeFromBufferedSource(serializer(), it.source().buffer())
}

/**
 * Serializes the object to a JSON string.
 */
inline fun <reified T> T.toJsonString(json: Json = jsonInstance): String = json.encodeToString(serializer(), this)

/**
 * Encodes the object to a Response Body.
 */
inline fun <reified T> T.toJsonRequestBody(json: Json = jsonInstance): RequestBody = toJsonString(json).toRequestBody(JSON_MEDIA_TYPE)
