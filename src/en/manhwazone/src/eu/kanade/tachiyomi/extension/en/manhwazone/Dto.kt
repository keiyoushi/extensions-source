package eu.kanade.tachiyomi.extension.en.manhwazone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
class LivewireRequestDto(
    @SerialName("_token") private val token: String,
    private val components: List<LivewireRequestComponentDto>,
)

@Serializable
class LivewireRequestComponentDto(
    private val snapshot: String,
    private val updates: JsonObject,
    private val calls: List<LivewireCallDto>,
)

@Serializable
class LivewireCallDto(
    private val path: String,
    private val method: String,
    private val params: List<String>,
)

@Serializable
class LivewireUpdateDto(
    val components: List<LivewireComponentDto> = emptyList(),
)

@Serializable
class LivewireComponentDto(
    val snapshot: String? = null,
)

@Serializable
class SnapshotDto(
    val data: SnapshotDataDto? = null,
)

@Serializable
class SnapshotDataDto(
    val chapters: JsonArray? = null,
)

@Serializable
class ChapterDto(
    val name: String? = null,
    val published: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
)
