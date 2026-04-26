package eu.kanade.tachiyomi.extension.en.bookwalker.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class TagDto(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(5) val tagKind: TagKind,
)

@JvmInline
@Serializable
value class TagKind(private val value: Int) {
    companion object {
        val GENRE = TagKind(1)
        val TAG = TagKind(2)
    }
}
