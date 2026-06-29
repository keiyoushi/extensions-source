package eu.kanade.tachiyomi.extension.en.manhwazone

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val chapters: JsonElement? = null,
)

@Serializable
class ChapterDto(
    val name: String? = null,
    val published: String? = null,
    @SerialName("web_url") val webUrl: String? = null,
)

@Serializable
class RsConfDto(
    val p: String? = null,
    val expire: String? = null,
    val signature: String? = null,
    val tt: Int? = null,
)
