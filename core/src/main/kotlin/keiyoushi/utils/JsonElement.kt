package keiyoushi.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

operator fun JsonElement?.get(key: String): JsonElement? = this?.jsonObject?.get(key)

operator fun JsonElement?.get(index: Int): JsonElement? = this?.jsonArray?.get(index)

val JsonElement.obj: JsonObject get() = jsonObject
val JsonElement.array: JsonArray get() = jsonArray
val JsonElement.string: String get() = jsonPrimitive.content
val JsonElement.stringOrNull: String? get() = jsonPrimitive.contentOrNull
val JsonElement.int: Int get() = jsonPrimitive.int
val JsonElement.intOrNull: Int? get() = jsonPrimitive.intOrNull
val JsonElement.long: Long get() = jsonPrimitive.long
val JsonElement.longOrNull: Long? get() = jsonPrimitive.longOrNull
val JsonElement.boolean: Boolean get() = jsonPrimitive.boolean
val JsonElement.booleanOrNull: Boolean? get() = jsonPrimitive.booleanOrNull

fun JsonObject.getString(key: String): String = getValue(key).jsonPrimitive.content

fun JsonObject.getStringOrNull(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull

fun JsonObject.getInt(key: String): Int = getValue(key).jsonPrimitive.int

fun JsonObject.getIntOrNull(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

fun JsonObject.getLong(key: String): Long = getValue(key).jsonPrimitive.long

fun JsonObject.getLongOrNull(key: String): Long? = get(key)?.jsonPrimitive?.longOrNull

fun JsonObject.getBoolean(key: String): Boolean = getValue(key).jsonPrimitive.boolean

fun JsonObject.getBooleanOrNull(key: String): Boolean? = get(key)?.jsonPrimitive?.booleanOrNull

fun JsonObject.getArray(key: String): JsonArray = getValue(key).jsonArray

fun JsonObject.getArrayOrNull(key: String): JsonArray? = get(key)?.jsonArray

fun JsonObject.getObject(key: String): JsonObject = getValue(key).jsonObject

fun JsonObject.getObjectOrNull(key: String): JsonObject? = get(key)?.jsonObject
