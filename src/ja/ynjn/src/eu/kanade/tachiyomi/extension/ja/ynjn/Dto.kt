package eu.kanade.tachiyomi.extension.ja.ynjn

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class RankingResponse(
    val data: RankingData,
)

@Serializable
class RankingData(
    val ranking: RankingTitles,
)

@Serializable
class RankingTitles(
    val titles: List<RankingDetails>,
)

@Serializable
class RankingDetails(
    val title: MangaDetails,
)

@Serializable
class DataResponse(
    val data: Data,
)

@Serializable
class Data(
    @SerialName("has_next") val hasNext: Boolean?,
    val titles: List<MangaDetails>,
    @SerialName("total_count") val totalCount: Int,
    val info: DataInfo?,
)

@Serializable
class DataInfo(
    val id: Int,
)

@Serializable
class MangaDetails(
    private val id: Int,
    @SerialName("image_url") private val imageUrl: String,
    private val name: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = imageUrl
    }
}

@Serializable
class TitleDetails(
    val data: TitleData,
)

@Serializable
class TitleData(
    val book: TitleBook,
)

@Serializable
class TitleBook(
    private val author: List<String>?,
    @SerialName("image_url") private val imageUrl: String?,
    private val name: String,
    private val summary: String?,
    private val tags: List<Tags>?,
    @SerialName("title_id") private val titleId: Int,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString()
        title = name
        author = this@TitleBook.author?.toString()
        description = summary
        genre = tags?.joinToString { it.value }
        thumbnail_url = imageUrl
    }
}

@Serializable
class Tags(
    val value: String,
)

@Serializable
class ChapterDetails(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val episodes: List<Episode>,
)

@Serializable
class Episode(
    private val id: Int,
    private val name: String,
    @SerialName("reading_condition") val readingCondition: String,
) {
    fun toSChapter(titleId: String): SChapter = SChapter.create().apply {
        url = "$id#$titleId"
        val isPaid = readingCondition != "EPISODE_READ_CONDITION_FREE"
        name = if (isPaid) {
            "🔒 ${this@Episode.name}"
        } else {
            this@Episode.name
        }
    }
}

@Serializable
class ViewerDetails(
    val data: ViewerData,
)

@Serializable
class ViewerData(
    val pages: List<ViewerPage>,
)

@Serializable
class ViewerPage(
    @SerialName("manga_page") val mangaPage: MangaPage?,
)

@Serializable
class MangaPage(
    @SerialName("page_image_url") val pageImageUrl: String,
    @SerialName("page_number") val pageNumber: Int,
)
