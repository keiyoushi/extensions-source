package eu.kanade.tachiyomi.extension.ja.cycomi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class NextData<T>(
    val props: Props<T>,
)

@Serializable
class Props<T>(
    val pageProps: T,
)

@Serializable
class RankingList(
    val rankingTitleList: List<TitleList>,
)

@Serializable
class MangaData(
    val data: MangaTitles,
)

@Serializable
class MangaTitles(
    val titles: List<TitleList>,
)

@Serializable
class TitleList(
    private val titleId: Int,
    private val titleName: String,
    private val image: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        thumbnail_url = image
    }
}

@Serializable
class MangaResponse(
    val data: MangaDetails,
)

@Serializable
class MangaDetails(
    private val author: String?,
    private val body: String?,
    private val image: String?,
    private val titleId: Int,
    private val titleName: String,
    private val serialType: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        author = this@MangaDetails.author
        description = body
        thumbnail_url = image
        status = if (serialType == "end" || serialType == "shot") SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class ChapterListResponse(
    val data: List<ChapterDetails>,
    val nextCursor: Long? = null,
)

@Serializable
class ChapterDetails(
    private val id: Int,
    private val titleId: Int,
    private val name: String,
    private val subName: String?,
    private val startAt: Long,
    private val purchaseUseCoin: Int?,
    private val rentalUseCoin: Int?,
    private val expirationAt: Long?,
) {
    val isLocked: Boolean
        get() = (purchaseUseCoin != 0 || rentalUseCoin != 0) && expirationAt == null

    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "$id#$titleId"
        val lockPrefix = if (isLocked) "🔒 " else ""
        val sub = if (subName.isNullOrEmpty()) "" else " - $subName"
        name = "$lockPrefix${this@ChapterDetails.name}$sub"
        date_upload = startAt
    }
}

@Serializable
class ViewerResponse(
    val data: ViewerData,
)

@Serializable
class ViewerData(
    val pages: List<ViewerPages>,
)

@Serializable
class ViewerPages(
    val image: String,
    val pageNumber: Int,
)

@Suppress("unused")
@Serializable
class ViewerRequestBody(
    val titleId: Int,
    val chapterId: Int,
)
