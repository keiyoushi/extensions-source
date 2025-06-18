package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
data class IndexResponse(
    val status: String,
    val error: String,
    val focus: List<IndexItem>,
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
data class IndexItem(
    val magId: String,
    val magName: String,
    val magPic: String,
    val magDate: String,
    val thumbPic: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@IndexItem.magName
        author = this@IndexItem.magName.split(" ")[0]
        thumbnail_url = this@IndexItem.magPic
        url = "/show.php?a=${this@IndexItem.magId}"
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
    fun toPage(i: Int): Page = Page(i, this@ShowItem.magPic, this@ShowItem.magPic)
}

@Serializable
data class SearchItem(
    val magId: String,
    val magName: String,
    val magDate: String,
    val pubdate: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SearchItem.magName
        author = this@SearchItem.magName.split(" ")[0]
        url = "/show.php?a=${this@SearchItem.magId}"
    }
}
