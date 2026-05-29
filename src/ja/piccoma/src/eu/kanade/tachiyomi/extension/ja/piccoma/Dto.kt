package eu.kanade.tachiyomi.extension.ja.piccoma

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable
class SearchResponseDto(
    val data: SearchDataDto,
)

@Serializable
class SearchDataDto(
    val products: List<SearchProductDto>,
    @SerialName("total_page") val totalPage: Int,
)

@Serializable
class SearchProductDto(
    private val id: Int,
    private val title: String,
    private val img: String?,
    @SerialName("is_audio") val isAudio: Int?,
    @SerialName("is_anime") val isAnime: Int?,
) {
    fun toSManga() = SManga.create().apply {
        url = "/web/product/$id"
        title = this@SearchProductDto.title
        thumbnail_url = "https:$img".toHttpUrl().newBuilder().setPathSegment(4, "cover_x3").toString()
    }
}

@Serializable
class PDataDto(
    val img: List<PDataImageDto>?,
    val contents: List<PDataImageDto>?,
    val isScrambled: Boolean,
)

@Serializable
class PDataImageDto(
    val path: String,
)
