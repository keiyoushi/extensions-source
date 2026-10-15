package eu.kanade.tachiyomi.extension.zh.ikmmh

import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val totalPage: String = "1",
    val length: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val url: String,
    val name: String,
    val stime: String? = null,
)

@Serializable
class PicsDto(
    val code: Int = 0,
    val data: PicsData = PicsData(),
)

@Serializable
class PicsData(
    val limit: Int = 0,
    val offset: Int = 0,
    val pic: List<PicDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
class PicDto(
    val pic: String,
    val id: String = "",
)
