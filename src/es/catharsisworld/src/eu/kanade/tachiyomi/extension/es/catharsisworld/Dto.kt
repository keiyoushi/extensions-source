package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.multisrc.catharsis.TilePlan
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The chapter loader's encrypted field
@Serializable
class ReaderDataDto(
    @SerialName("p") val pageTokens: List<String>,
    @SerialName("r") val heartbeat: String,
)

@Serializable
class HeartbeatRequest(
    @SerialName("h") private val heartbeat: String,
)

@Serializable
class HeartbeatDto(
    @SerialName("l") val liveToken: String,
)

// What the reader's tile plan function returns
@Serializable
class TilePlanDto(
    @SerialName("X") val cols: List<Int>,
    @SerialName("Y") val rows: List<Int>,
    val colPerm: List<Int>,
    val rowPerm: List<Int>,
)

fun TilePlanDto.toTilePlan() = TilePlan(cols.toIntArray(), rows.toIntArray(), colPerm.toIntArray(), rowPerm.toIntArray())
