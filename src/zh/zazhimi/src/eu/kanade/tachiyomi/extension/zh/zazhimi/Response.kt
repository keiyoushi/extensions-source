package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.Serializable

@Serializable
class IndexResponse(
    val new: List<NewItem>,
)

@Serializable
class ShowResponse(
    val content: List<ShowItem>,
)

@Serializable
class SearchResponse(
    val magazine: List<SearchItem>,
)

@Serializable
class NewItem(
    private val magId: String,
    private val magName: String,
    private val magCover: String,
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
class ShowItem(
    val magId: String,
    val magName: String,
    val magPic: String,
) {
    fun toPage(i: Int): Page = Page(i, imageUrl = this@ShowItem.magPic)
}

@Serializable
class SearchItem(
    private val magId: String,
    private val magName: String,
    private val magCover: String? = null,
    private val magDate: String? = null,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@SearchItem.magName
        author = this@SearchItem.magName.split(" ").firstOrNull()
        url = "/show.php?a=${this@SearchItem.magId}"
        // search results have no cover, magDate is the image folder of the first page
        thumbnail_url = magCover ?: magDate?.let { "https://img2020.zazhimi.net/aazzmpic-l/${it}001.jpg" }
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }
}
