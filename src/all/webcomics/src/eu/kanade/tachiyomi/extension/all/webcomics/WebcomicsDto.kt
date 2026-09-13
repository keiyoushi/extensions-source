package eu.kanade.tachiyomi.extension.all.webcomics

import kotlinx.serialization.Serializable

@Serializable
class DataWrapper<T>(
    val data: T,
)

@Serializable
class BookDto(
    val name: String,
    val cover: String,
    val category: List<String>,
    val author: String,
    val description: String,
    val status: String,
)

@Serializable
class ChapterListDto(
    val list: List<ChapterDto>,
)

@Serializable
class ChapterDto(
    val chapter_id: String,
    val index: Int,
    val is_pay: Boolean,
    val name: String,
    val update_time: Long,
)

@Serializable
class ImageListDto(
    val base_url: String,
    val images: List<ImageDto>,
)

@Serializable
class ImageDto(
    val url: String,
)

@Serializable
class UserAgentList(
    val desktop: List<String>,
)
