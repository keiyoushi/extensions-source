package eu.kanade.tachiyomi.extension.en.complexbowler

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ImgchestPageDto(private val props: ImgchestPropsDto) {
    fun toPages() = props.post.files.sortedBy { it.position }.mapIndexed { index, file -> Page(index, imageUrl = file.link) }
}

@Serializable
class ImgchestPropsDto(val post: ImgchestPostDto)

@Serializable
class ImgchestPostDto(val files: List<ImgchestFileDto>)

@Serializable
class ImgchestFileDto(val link: String, val position: Int)
