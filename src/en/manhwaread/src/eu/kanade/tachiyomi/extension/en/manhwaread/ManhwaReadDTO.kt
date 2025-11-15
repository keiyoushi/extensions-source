package eu.kanade.tachiyomi.extension.en.manhwaread

import kotlinx.serialization.Serializable

@Serializable
class Results(
    val results: List<Result>,
)

@Serializable
class Result(
    val id: Int,
    val text: String,
)

@Serializable
class ImageBaseUrlDto(
    val base: String,
)

@Serializable
class PagesDto(
    val data: Data,
) {
    @Serializable
    class Data(
        val chapter: Chapter,
    ) {
        @Serializable
        class Chapter(
            val images: List<Image>,
        ) {
            @Serializable
            class Image(
                val src: String,
            )
        }
    }
}
