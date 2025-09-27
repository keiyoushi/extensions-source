package eu.kanade.tachiyomi.extension.ja.ganma

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GraphQLResponse<T>(val data: T)

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

@Serializable
object EmptyVariables

@Serializable
class SearchVariables(
    val keyword: String,
    val first: Int,
    val after: String? = null,
)

@Serializable
class DayOfWeekVariables(
    val dayOfWeek: String,
    val first: Int,
    val after: String? = null,
)

@Serializable
class FinishedVariables(
    val first: Int,
    val after: String? = null,
)

@Serializable
class MagazineDetailVariables(
    val magazineIdOrAlias: String,
)

@Serializable
class StoryInfoListVariables(
    val magazineIdOrAlias: String,
    val first: Int,
    val after: String? = null,
)

@Serializable
class StoryReaderVariables(
    val magazineIdOrAlias: String,
    val storyId: String,
)

@Serializable
class MangaItemDto(
    val alias: String,
    val title: String,
    val todaysJacketImageURL: String? = null,
    val rectangleWithLogoImageURL: String? = null,
)

@Serializable
class HomeDto(
    val ranking: Ranking,
    val latestTotalRanking10: List<MangaItemDto>,
)

@Serializable
class Ranking(
    val totalRanking: List<MangaItemDto>,
)

@Serializable
class SearchEdge(
    val node: MangaItemDto?,
)

@Serializable
class StoryInfoMagazine(
    val magazine: MangaItemDto,
)

@Serializable
class FinishedEdge(
    val node: MangaItemDto,
)

@Serializable
class SearchDto(
    val searchComic: SearchResult,
)

@Serializable
class SearchResult(
    val edges: List<SearchEdge>,
    val pageInfo: PageInfo,
)

@Serializable
class SerialResponseDto(
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
    val storyInfo: StoryInfoMagazine,
)

@Serializable
class FinishedResponseDto(
    val magazinesByCategory: FinishedCategory,
)

@Serializable
class FinishedCategory(
    val magazines: FinishedConnection,
)

@Serializable
class FinishedConnection(
    val edges: List<FinishedEdge>,
    val pageInfo: PageInfo,
)

@Serializable
class MagazineDetailDto(
    val magazine: MagazineDetail,
)

@Serializable
class MagazineDetail(
    val title: String,
    val alias: String,
    val authorName: String? = null,
    val description: String,
    val isFinished: Boolean,
    val todaysJacketImageURL: String? = null,
)

@Serializable
class ChapterListDto(
    val magazine: MagazineWithChapters,
)

@Serializable
class MagazineWithChapters(
    val storyInfos: StoryInfoConnection,
)

@Serializable
class StoryInfoConnection(
    val edges: List<StoryInfoEdge>,
    val pageInfo: PageInfo,
)

@Serializable
class StoryInfoEdge(
    val node: StoryInfoNode,
)

@Serializable
class StoryInfoNode(
    val storyId: String,
    val title: String,
    val subtitle: String? = null,
    val contentsRelease: Long,
    val isPurchased: Boolean,
    val contentsAccessCondition: ContentsAccessCondition,
    val isSellByStory: Boolean,
)

@Serializable
class ContentsAccessCondition(
    @SerialName("__typename")
    val typename: String,
    val info: PurchaseInfo? = null,
)

@Serializable
class PurchaseInfo(
    val coins: Int,
)

@Serializable
class PageInfo(
    val hasNextPage: Boolean,
    val endCursor: String? = null,
)

@Serializable
class PageListDto(
    val magazine: MagazinePages,
)

@Serializable
class MagazinePages(
    val storyContents: StoryContents,
)

@Serializable
class StoryContents(
    val pageImages: PageImages? = null,
    val error: String? = null,
    val afterword: Afterword? = null,
)

@Serializable
class PageImages(
    val pageCount: Int,
    val pageImageBaseURL: String,
    val pageImageSign: String,
)

@Serializable
class Afterword(
    val imageURL: String? = null,
)
