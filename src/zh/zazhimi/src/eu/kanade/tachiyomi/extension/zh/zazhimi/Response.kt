package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable

@Serializable
data class IndexResponse(
    val status: String,
    val error: String,
    val new: List<NewItem>,
)

@Serializable
data class ShowResponse(
    val status: String,
    val error: String,
    val content: List<ShowItem>,
)

@Serializable
data class SearchResponse(
    val status: String,
    val error: String,
    val magazine: List<SearchItem>,
)

@Serializable
data class NewItem(
    val magId: String,
    val magName: String,
    val magCover: String,
    val magDate: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@NewItem.magName
        author = this@NewItem.magName.split(" ")[0]
        thumbnail_url = this@NewItem.magCover
        url = "/show.php?a=${this@NewItem.magId}"
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }
}

@Serializable
data class ShowItem(
    val magId: String,
    val magName: String,
    val typeId: String,
    val typeName: String,
    val cateId: String,
    val magPic: String,
    val pageUrl: String,
    val pageThumbUrl: String,
) {
    fun toPage(i: Int): Page = Page(i, imageUrl = this@ShowItem.magPic)
}

@Serializable
data class SearchItem(
    val magId: String,
    val magName: String,
    val magDate: String,
    val magCover: String?,
    val pubdate: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SearchItem.magName
        author = this@SearchItem.magName.split(" ").firstOrNull()
        url = "/show.php?a=${this@SearchItem.magId}"
        thumbnail_url = magCover
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}
