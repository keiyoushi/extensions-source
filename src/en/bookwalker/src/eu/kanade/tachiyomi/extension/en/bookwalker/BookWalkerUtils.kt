package eu.kanade.tachiyomi.extension.en.bookwalker

import keiyoushi.utils.toRequestBodyProto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody

// Protobuf is supposed to use application/protobuf, but for some reason BW doesn't
val protoMediaType = "application/proto".toMediaType()

inline fun <reified T : Any> T.toProtoRequestBody(): RequestBody = toRequestBodyProto(mediaType = protoMediaType)
