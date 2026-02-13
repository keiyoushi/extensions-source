package eu.kanade.tachiyomi.extension.ja.mangaupjapan

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.Serializable

@Serializable
class ChapterData(
    private val id: Int,
    private val name: String,
    private val subName: String?,
    private val publishingStatus: Int?,
) {
    fun toSChapter(titleId: String): SChapter = SChapter.create().apply {
        val isLocked = publishingStatus != 3 // 3 = free and full chapter
        val lockPrefix = if (isLocked) "🔒 (Preview) " else ""
        val sub = if (subName.isNullOrEmpty()) "" else "$subName - "
        name = "$lockPrefix$sub${this@ChapterData.name}"
        url = "$id#$titleId"
    }
}

@Serializable
class PageData(
    val content: PageContent,
)

@Serializable
class PageContent(
    val value: PageValue?,
)

@Serializable
class PageValue(
    val imageUrl: String?,
)
