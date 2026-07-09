package eu.kanade.tachiyomi.extension.all.onisaga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaLivewireRequest(
    @SerialName("_token") val token: String,
    val components: List<Component>,
) {
    @Serializable
    class Component(
        val snapshot: String,
        val updates: PostFilterUpdatesDto,
        val calls: List<LivewireCall>,
    )
}

@Serializable
class ChapterLivewireRequest(
    @SerialName("_token") val token: String,
    val components: List<Component>,
) {
    @Serializable
    class Component(
        val snapshot: String,
        val updates: ChapterUpdatesDto,
        val calls: List<LivewireCall>,
    )
}

@Serializable
class LivewireCall(
    val type: String = "call",
    val path: String = "",
    val method: String,
    val params: List<String> = emptyList(),
)

@Serializable
class LivewireResponse(
    val components: List<ComponentResponse> = emptyList(),
) {
    @Serializable
    class ComponentResponse(
        val effects: Effects = Effects(),
        val snapshot: String = "",
    )

    @Serializable
    class Effects(
        val html: String? = null,
    )
}

@Serializable
class PostFilterUpdatesDto(
    val platform: String = "",
    val status: String = "",
    val sort: String = "created_at",
    @SerialName("min_chapters") val minChapters: String = "",
    val group: String? = null,
    @SerialName("release_start") val releaseStart: String? = null,
    @SerialName("release_end") val releaseEnd: String? = null,
    val genre: List<String> = emptyList(),
    var excludeGenre: List<String> = emptyList(),
)

@Serializable
class PageApiResponse(
    val url: String? = null,
    val order: Int? = null,
    val message: String? = null,
)

@Serializable
class ChapterUpdatesDto(
    val language: String = "",
)
