package eu.kanade.tachiyomi.extension.vi.nhattruyen

import kotlinx.serialization.Serializable

@Serializable
class ChapterDTO(
    val data: ArrayList<Data> = arrayListOf(),
)

@Serializable
class Data(
    val chapter_name: String,
    val chapter_slug: String,
    val updated_at: String,

)
