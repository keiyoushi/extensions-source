package eu.kanade.tachiyomi.extension.en.jnovel

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class E4PQSTicket(
    @ProtoNumber(1) val type: Int,
    @ProtoNumber(2) val contentId: String,
    @ProtoNumber(3) val consumer: Consumer,
    @ProtoNumber(4) val timestamp: Timestamp,
    @ProtoNumber(5) val wrapper: E4PQSWrapper,
)

@Serializable
class Consumer(
    @ProtoNumber(6) val child: List<Int>,
)

@Serializable
class Timestamp(
    @ProtoNumber(1) val seconds: Long,
)

@Serializable
class E4PQSWrapper(
    // TODO("")
)
