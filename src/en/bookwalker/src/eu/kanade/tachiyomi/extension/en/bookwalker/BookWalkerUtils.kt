package eu.kanade.tachiyomi.extension.en.bookwalker

import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

// Protobuf is supposed to use application/protobuf, but for some reason BW doesn't
val protoMediaType = "application/proto".toMediaType()

inline fun <reified T : Any> T.toProtoRequestBody(): RequestBody = ProtoBuf.encodeToByteArray(this)
    .toRequestBody(protoMediaType)
inline fun <reified T> Response.parseProtoAs(): T = ProtoBuf.decodeFromByteArray(body.bytes())
