package eu.kanade.tachiyomi.extension.all.e621

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Pool(
    val id: Int,
    val name: String,
    val description: String = "",
    @SerialName("post_ids") val postIds: List<Int> = emptyList(),
    @SerialName("is_active") val isActive: Boolean? = null,
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
class Post(
    val id: Int,
    val flags: Flags,
    val files: Files,
    val tags: Tags,
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
    @SerialName("has_sample") val hasSample: Boolean,
)

@Serializable
class OriginalFile(
    val url: String?,
)

@Serializable
class ScaledFile(
    val width: Int,
    val height: Int,
    val webp: String?,
)

@Serializable
class Tags(
    val general: List<String> = emptyList(),
    val artist: List<String> = emptyList(),
    val copyright: List<String> = emptyList(),
    val character: List<String> = emptyList(),
    val species: List<String> = emptyList(),
    val lore: List<String> = emptyList(),
    val meta: List<String> = emptyList(),
) {
    val allTags: List<String>
        get() = artist + character + copyright + general + lore + meta + species
}

@Serializable
class UserMeResponse(
    @SerialName("blacklisted_tags") private val _blacklistedTags: String? = null,
    private val user: UserMeData? = null,
) {
    val blacklistedTags: String?
        get() = _blacklistedTags ?: user?.blacklistedTags
}

@Serializable
class UserMeData(
    @SerialName("blacklisted_tags") val blacklistedTags: String? = null,
)
