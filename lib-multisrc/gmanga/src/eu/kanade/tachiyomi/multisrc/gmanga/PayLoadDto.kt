package eu.kanade.tachiyomi.multisrc.gmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SearchPayload(
    private val oneshot: OneShot,
    private val title: String,
    private val page: Int,
    @SerialName("manga_types") private val mangaTypes: IncludeExclude,
    @SerialName("story_status") private val storyStatus: IncludeExclude,
    @SerialName("translation_status") val tlStatus: IncludeExclude,
    private val categories: IncludeExclude,
    private val chapters: MinMax,
    private val dates: StartEnd,
)

@Serializable
class OneShot(
    private val value: Boolean,
)

@Serializable
class IncludeExclude(
    private val include: List<String?>,
    private val exclude: List<String?>,
)

@Serializable
class MinMax(
    private val min: String,
    private val max: String,
)

@Serializable
class StartEnd(
    private val start: String,
    private val end: String,
)
