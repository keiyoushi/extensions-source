package eu.kanade.tachiyomi.extension.en.quantumscans

import kotlinx.serialization.Serializable

@Serializable
class GenreDto(val id: Int, val name: String)

@Serializable
class ChapterImages(val API_Response: ChapterResponse)

@Serializable
class ChapterResponse(val chapter: ChapterData)

@Serializable
class ChapterData(val images: List<PageDto>)

@Serializable
class PageDto(val url: String, val order: Int)
