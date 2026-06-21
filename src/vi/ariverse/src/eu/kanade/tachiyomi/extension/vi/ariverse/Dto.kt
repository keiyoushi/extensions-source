package eu.kanade.tachiyomi.extension.vi.ariverse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class StoryListResponse(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    @SerialName("per_page") val perPage: Int,
    val total: Int,
    val data: List<Story>,
)

@Serializable
class HotStoryListResponse(
    val data: List<Story>,
)

@Serializable
class StoryDetailResponse(
    val data: StoryDetail,
)

@Serializable
class StoryDetail(
    val id: Int,
    val slug: String,
    val title: String,
    @SerialName("title_alt") val titleAlt: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("is_18_plus") val is18Plus: Boolean = false,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<Genre>? = null,
    val team: Team? = null,
)

@Serializable
class Story(
    val id: Int,
    val slug: String,
    val title: String,
    @SerialName("title_alt") val titleAlt: String? = null,
    val description: String? = null,
    val status: String? = null,
    @SerialName("cover_path") val coverPath: String? = null,
    @SerialName("is_18_plus") val is18Plus: Boolean = false,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<Genre>? = null,
    val team: Team? = null,
)

@Serializable
class Genre(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
class Team(
    val id: Int,
    val name: String,
    val slug: String,
)

@Serializable
class GenreListResponse(
    val data: List<Genre>,
)

@Serializable
class ChapterListResponse(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val story: StoryInfo,
    val chapters: List<Chapter>,
)

@Serializable
class StoryInfo(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
)

@Serializable
class Chapter(
    val id: Int,
    @SerialName("story_id") val storyId: Int,
    val volume: Int? = null,
    val number: Double,
    val title: String,
    val slug: String,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("published_at") val publishedAt: String? = null,
)

@Serializable
class ChapterDetailResponse(
    val data: ChapterDetail,
)

@Serializable
class ChapterDetail(
    val id: Int,
    @SerialName("story_id") val storyId: Int,
    val volume: Int? = null,
    val number: Double,
    val title: String,
    val slug: String,
    @SerialName("is_public") val isPublic: Boolean = true,
    @SerialName("published_at") val publishedAt: String? = null,
    val content: List<String>? = null,
    val story: StoryInfo? = null,
    @SerialName("content_locked") val contentLocked: Boolean = false,
    @SerialName("content_gating_message") val contentGatingMessage: String? = null,
)
