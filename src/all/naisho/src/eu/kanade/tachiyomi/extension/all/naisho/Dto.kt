package eu.kanade.tachiyomi.extension.all.naisho

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class UpdateDto(
    @SerialName("_token") private val token: String,
    private val components: List<ComponentUpdateDto>,
)

@Serializable
class ComponentUpdateDto(
    private val snapshot: String,
    private val updates: Map<String, String>,
    private val calls: List<CallDto>,
)

@Serializable
class CallDto(
    private val method: String,
    private val params: List<String>,
    private val metadata: MetadataDto,
)

@Serializable
class MetadataDto(private val island: IslandDto?)

@Serializable
class IslandDto(
    private val name: String,
    private val mode: String,
)

@Serializable
class UpdateResultDto(val components: List<ComponentResultDto>)

@Serializable
class ComponentResultDto(
    val snapshot: String,
    val effects: EffectsDto,
)

@Serializable
class EffectsDto(
    val dispatches: List<DispatchDto> = emptyList(),
    val islandFragments: List<String> = emptyList(),
)

@Serializable
class DispatchDto(
    val name: String,
    val params: JsonElement,
)

@Serializable
class PostsDto(val items: List<PostDto>)

@Serializable
class PostDto(
    private val slug: String,
    private val name: String,
    private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$slug"
        title = name
        thumbnail_url = cover
    }
}

@Serializable
class ToastDto(val message: String)

@Serializable
class GridSnapshotDto(val data: GridDataDto)

@Serializable
class GridDataDto(val hasMore: Boolean)

@Serializable
class PageSnapshotDto(val memo: MemoDto)

@Serializable
class MemoDto(val islands: List<IslandNameDto>)

@Serializable
class IslandNameDto(val name: String)

@Serializable
class PageDto(val src: String)
