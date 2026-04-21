package eu.kanade.tachiyomi.extension.en.jnovel

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

// in e4p_client.js
@Serializable
class E4PQSTicket(
    @ProtoNumber(1) val type: Int,
    @ProtoNumber(2) val contentId: String,
    @ProtoNumber(3) val consumer: String,
    @ProtoNumber(4) val expires: Timestamp = Timestamp(),
    @ProtoNumber(5) val child: E4PQSWrapper = E4PQSWrapper(),
)

@Serializable
class Timestamp(
    @ProtoNumber(1) val seconds: Long = 0L,
)

@Serializable
class E4PQSWrapper(
    @ProtoNumber(1) val type: Int = 0,
    @ProtoNumber(2) val iv: ByteArray = EMPTY,
    @ProtoNumber(3) val checksum: ByteArray = EMPTY,
    @ProtoNumber(4) val data: ByteArray = EMPTY,
    @ProtoNumber(5) val dataType: Int = 0,
    @ProtoNumber(6) val dictChecksum: Int = 0,
) {
    companion object {
        private val EMPTY = ByteArray(0)
    }
}

object TicketType {
    const val PLAIN_UNSPECIFIED = 0
    const val OPAQUE = 1
    const val TDRM_V1 = 2
}

object WrapperType {
    const val PLAIN_UNSPECIFIED = 0
    const val OPAQUE = 1
    const val CDRM_V1 = 2
}

object DataType {
    const val UNSPECIFIED = 0
    const val WEBPUB = 1
    const val PROTOPUB = 2
    const val PROTOPUB_ZSTD = 3
    const val PROTOTICKET = 4
    const val PROTOPUB_ZLIB = 5
    const val HTML = 101
}

@Serializable
class ProtoPub(
    @ProtoNumber(2) val spine: List<Link> = emptyList(),
)

@Serializable
class Link(
    @ProtoNumber(1) val variants: List<Variant> = emptyList(),
)

@Serializable
class Variant(
    @ProtoNumber(1) val link: String = "",
    @ProtoNumber(2) val image: ImageProps?,
)

@Serializable
class ImageProps(
    @ProtoNumber(1) val width: Int = 0,
    @ProtoNumber(2) val height: Int = 0,
    @ProtoNumber(3) val drm: EDRM?,
)

@Serializable
class EDRM(
    @ProtoNumber(1) val version: Int = 0,
    @ProtoNumber(2) val kid: String = "",
    @ProtoNumber(3) val iv: ByteArray = EMPTY,
    @ProtoNumber(4) val challenge: ByteArray = EMPTY,
) {
    companion object {
        private val EMPTY = ByteArray(0)
    }
}
