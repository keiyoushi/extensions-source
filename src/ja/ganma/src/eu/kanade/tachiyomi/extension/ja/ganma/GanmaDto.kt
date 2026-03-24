package eu.kanade.tachiyomi.extension.ja.ganma

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GraphQLResponse<T>(
    val data: T,
)

@Suppress("unused")
@Serializable
class Payload<T>(
    val operationName: String,
    val variables: T,
    val extensions: Extensions,
) {
    @Serializable
    class Extensions(
        val persistedQuery: PersistedQuery,
    ) {
        @Serializable
        class PersistedQuery(
            val version: Int,
            val sha256Hash: String,
        )
    }
}

// Variables
@Serializable
object EmptyVariables

@Suppress("unused")
@Serializable
class SearchVariables(
    val keyword: String,
    val after: String?,
)

@Suppress("unused")
@Serializable
class DayOfWeekVariables(
    val dayOfWeek: String,
    val after: String?,
)

@Suppress("unused")
@Serializable
class FinishedVariables(
    val after: String?,
)

@Suppress("unused")
@Serializable
class MagazineDetailVariables(
    val magazineIdOrAlias: String,
)

@Suppress("unused")
@Serializable
class ChapterListVariables(
    val magazineIdOrAlias: String,
    val first: Int,
    val after: String?,
)

@Suppress("unused")
@Serializable
class ViewerVariables(
    val magazineIdOrAlias: String,
    val storyId: String,
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
    val alias: String,
    private val title: String,
    private val authorName: String?,
    private val description: String?,
    private val isFinished: Boolean?,
    @SerialName("squareWithLogoImageURL") private val squareWithLogoImageUrl: String?,
    private val rectangleWithLogoImageURL: String?,
    private val magazineTags: List<Tags>?,
    val isWebOnlySensitive: Boolean?,
) {
    fun toSManga() = SManga.create().apply {
        title = this@Details.title
        author = authorName
        description = this@Details.description
        genre = magazineTags?.joinToString { it.name }
        status = if (isFinished == true) SManga.COMPLETED else SManga.ONGOING
        thumbnail_url = squareWithLogoImageUrl ?: rectangleWithLogoImageURL
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
) {
    val isLocked: Boolean
        get() = isPurchased == false && (
            (contentsAccessCondition?.typename != "FreeStoryContentsAccessCondition") ||
                (contentsAccessCondition.info?.coins != null && contentsAccessCondition.info.coins != 0)
            )

    fun toSChapter(slug: String) = SChapter.create().apply {
        val lock = if (isLocked) "\uD83E\uDE99 " else ""
        val chapterName = if (!subtitle.isNullOrEmpty()) "$title $subtitle" else title
        url = "$slug/$storyId"
        name = lock + chapterName
        date_upload = contentsRelease
    }
}

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
