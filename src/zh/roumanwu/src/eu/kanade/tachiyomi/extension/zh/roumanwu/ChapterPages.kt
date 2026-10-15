package eu.kanade.tachiyomi.extension.zh.roumanwu

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ChapterPages(
    private val imagePaths: List<String>,
) {
    fun toPageList() = imagePaths.mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }
}
