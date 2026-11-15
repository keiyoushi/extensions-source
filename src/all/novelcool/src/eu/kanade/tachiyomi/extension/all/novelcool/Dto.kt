package eu.kanade.tachiyomi.extension.all.novelcool

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class NovelCoolBrowsePayload(
    private val appId: String,
    @SerialName("keyword") private val query: String? = null,
    private val lang: String,
    @SerialName("lc_type") private val type: String,
    private val page: String,
    @SerialName("page_size") private val size: String,
    private val secret: String,
)

@Serializable
class NovelCoolBrowseResponse(
    val list: List<Manga>? = emptyList(),
)

@Serializable
class Manga(
    val url: String,
    private val name: String,
    private val cover: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        thumbnail_url = cover
    }
}
