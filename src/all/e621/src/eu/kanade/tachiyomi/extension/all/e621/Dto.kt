package eu.kanade.tachiyomi.extension.all.e621

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pool(
    val id: Int,
    val name: String,
    val description: String?,
    @SerialName("post_ids") val postIds: List<Int>,
    @SerialName("is_active") val isActive: Boolean?,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
class Post(
    val id: Int,
    val flags: Flags,
    val files: Files,
    val tags: Map<String, List<String>>,
    val stats: Stats,
    @SerialName("pools") val poolIds: List<Int>,
    @SerialName("created_at") val createdAt: String,
    val rating: String,
)

@Serializable
class Stats(
    val score: Score,
)

@Serializable
class Score(
    val total: Int,
)

@Serializable
class Flags(
    val deleted: Boolean,
)

@Serializable
class Files(
    val meta: FileMeta,
    val original: OriginalFile,
    val preview: ScaledFile,
    val sample: ScaledFile,
)

@Serializable
class FileMeta(
    val ext: String,
)

@Serializable
class OriginalFile(
    val url: String?,
)

@Serializable
class ScaledFile(
    val webp: String?,
)

@Serializable
class UserMeResponse(
    @SerialName("blacklisted_tags") private val _blockedTags: String?,
    private val user: UserMeData?,
) {
    val blockedTags: String?
        get() = _blockedTags ?: user?.blockedTags
}

@Serializable
class UserMeData(
    @SerialName("blacklisted_tags") val blockedTags: String?,
)
