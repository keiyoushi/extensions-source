package eu.kanade.tachiyomi.extension.zh.tencentcomics

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

@Serializable
class ChapterData(
    private val chapter: ChapterInfo,
    private val picture: List<PageInfo>,
) {
    fun toPageList(): List<Page> {
        chapter.ensureReadable()
        return picture.mapIndexed { index, info -> info.toPage(index) }
    }
}

@Serializable
class ChapterInfo(
    private val canRead: Boolean,
) {
    fun ensureReadable() {
        if (!canRead) throw Exception("[此章节为付费内容]")
    }
}

@Serializable
class PageInfo(
    private val url: String,
) {
    fun toPage(index: Int): Page = Page(index, imageUrl = url)
}
