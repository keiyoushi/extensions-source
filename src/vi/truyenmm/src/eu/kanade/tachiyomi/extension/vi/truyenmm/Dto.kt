package eu.kanade.tachiyomi.extension.vi.truyenmm

import kotlinx.serialization.Serializable

@Serializable
class TruyenMMGetTopicResponse(
    val topic: TruyenMMTopic? = null,
)

@Serializable
class TruyenMMTopic(
    val _id: String? = null,
    val chapters: List<TruyenMMChapter>? = null,
    val genres: List<String>? = null,
    val img: String? = null,
    val name: String? = null,
)

@Serializable
class TruyenMMChapter(
    val name: String? = null,
    val id: String? = null,
    val update_time: Long? = null,
)
