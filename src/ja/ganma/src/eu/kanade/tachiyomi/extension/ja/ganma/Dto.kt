package eu.kanade.tachiyomi.extension.ja.ganma

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Variables
@Serializable
object EmptyVariables

@Suppress("unused")
@Serializable
class SearchVariables(
    private val keyword: String,
    private val after: String?,
)

@Suppress("unused")
@Serializable
class DayOfWeekVariables(
    private val dayOfWeek: String,
    private val after: String?,
)

@Suppress("unused")
@Serializable
class FinishedVariables(
    private val after: String?,
)

@Suppress("unused")
@Serializable
class MagazineDetailVariables(
    private val magazineIdOrAlias: String,
)

@Suppress("unused")
@Serializable
class ChapterListVariables(
    private val magazineIdOrAlias: String,
    private val first: Int,
    private val after: String?,
)

@Suppress("unused")
@Serializable
class ViewerVariables(
    private val magazineIdOrAlias: String,
    private val storyId: String,
)

// Cursor
@Serializable
class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String?,
)

// Popular
@Serializable
class HomeDto(
    val ranking: RankingDto,
)

@Serializable
class RankingDto(
    val totalRanking: List<MangaItemDto>,
)

// Latest
@Serializable
class LatestResponse(
    val serialPerDayOfWeek: SerialPanel,
)

@Serializable
class SerialPanel(
    val panels: SerialConnection,
)

@Serializable
class SerialConnection(
    val edges: List<SerialEdge>,
    val pageInfo: PageInfo,
)

@Serializable
class SerialEdge(
    val node: SerialNode,
)

@Serializable
class SerialNode(
    val storyInfo: StoryInfoRef,
)

@Serializable
class StoryInfoRef(
    val magazine: MangaItemDto,
)

@Serializable
class FinishedResponseDto(
    val magazinesByCategory: FinishedCategoryDto,
)

@Serializable
class FinishedCategoryDto(
    val magazines: SearchConnection,
)

// Search
@Serializable
class SearchResponse(
    val searchComic: SearchConnection,
)

@Serializable
class SearchConnection(
    val edges: List<SearchEdge>,
    val pageInfo: PageInfo,
)

@Serializable
class SearchEdge(
    val node: MangaItemDto,
)

@Serializable
class MangaItemDto(
    private val alias: String,
    private val title: String,
    @SerialName("todaysJacketImageURL") private val todaysJacketImageUrl: String?,
    @SerialName("rectangleWithLogoImageURL") private val rectangleWithLogoImageUrl: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = alias
        title = this@MangaItemDto.title
        thumbnail_url = todaysJacketImageUrl ?: rectangleWithLogoImageUrl
    }
}

// Details
@Serializable
class DetailsResponse(
    val magazine: Details,
)

@Serializable
class Details(
    private val title: String,
    private val authorName: String?,
    private val description: String?,
    private val isFinished: Boolean?,
    @SerialName("squareWithLogoImageURL") private val squareWithLogoImageUrl: String?,
    @SerialName("rectangleWithLogoImageURL") private val rectangleWithLogoImageUrl: String?,
    private val magazineTags: List<Tags>?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@Details.title
        author = authorName
        description = this@Details.description
        genre = magazineTags?.joinToString { it.name }
        status = if (isFinished == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = squareWithLogoImageUrl ?: rectangleWithLogoImageUrl
    }
}

@Serializable
class Tags(
    val name: String,
)

// Chapters
@Serializable
class ChapterResponse(
    val magazine: ChapterInfos,
)

@Serializable
class ChapterInfos(
    val storyInfos: ChapterEdge,
)

@Serializable
class ChapterEdge(
    val edges: List<StoryInfoEdge>,
)

@Serializable
class StoryInfoEdge(
    val node: Chapters,
)

@Serializable
class Chapters(
    private val storyId: String,
    private val title: String,
    private val subtitle: String?,
    private val contentsRelease: Long,
    private val isPurchased: Boolean?,
    private val contentsAccessCondition: ContentsAccessCondition?,
    private val storyContents: StoryContents,
) {
    val isLocked: Boolean
        get() = isPurchased == false && (
            (contentsAccessCondition?.typename != "FreeStoryContentsAccessCondition") ||
                (contentsAccessCondition.info?.coins != null && contentsAccessCondition.info.coins != 0)
            )

    fun toSChapter(alias: String) = SChapter.create().apply {
        val lock = if (isLocked) "\uD83E\uDE99 " else ""
        val chapterName = if (!subtitle.isNullOrEmpty()) "$title $subtitle" else title
        url = storyId
        name = lock + chapterName
        date_upload = contentsRelease
        memo = buildJsonObject {
            put("alias", alias)
            put("desktop", storyContents.typename == "StoryContents")
        }
    }
}

@Serializable
class StoryContents(
    @SerialName("__typename") val typename: String,
)

@Serializable
class ContentsAccessCondition(
    @SerialName("__typename") val typename: String,
    val info: PurchaseInfo?,
)

@Serializable
class PurchaseInfo(
    val coins: Int?,
)

// Viewer
@Serializable
class ViewerResponse(
    val magazine: ViewerContent,
)

@Serializable
class ViewerContent(
    val storyContents: ViewerInfo,
)

@Serializable
class ViewerInfo(
    val pageImages: ViewerImages?,
    val error: String?,
    val afterword: Afterword?,
)

@Serializable
class ViewerImages(
    val pageCount: Int,
    @SerialName("pageImageBaseURL") val pageImageBaseUrl: String,
    val pageImageSign: String,
)

@Serializable
class Afterword(
    @SerialName("imageURL") val imageUrl: String?,
)
