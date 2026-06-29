package eu.kanade.tachiyomi.extension.ja.cycomi

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class NextData(
    val props: Props,
)

@Serializable
class Props(
    val pageProps: RankingList,
)

@Serializable
class RankingList(
    val rankingTitleList: List<TitleList>,
)

@Serializable
class MangaResponse(
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
class DetailsResponse(
    private val author: String?,
    private val body: String?,
    private val image: String?,
    private val titleId: Int,
    private val titleName: String,
    private val serialType: String?,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = titleId.toString()
        title = titleName
        author = this@DetailsResponse.author
        description = body
        thumbnail_url = image
        status = if (serialType == "end" || serialType == "shot") SManga.COMPLETED else SManga.ONGOING
    }
}

@Serializable
class ChapterListResponse(
    val id: Int,
    private val titleId: Int,
    private val name: String,
    private val subName: String?,
    private val startAt: Long?,
) {
    fun toSChapter(isLocked: Boolean): SChapter = SChapter.create().apply {
        url = "$id#$titleId"
        val lockPrefix = if (isLocked) "🔒 " else ""
        val sub = if (subName.isNullOrEmpty()) "" else " - $subName"
        name = "$lockPrefix${this@ChapterListResponse.name}$sub"
        date_upload = startAt ?: 0L
    }
}

@Serializable
class StatusResponse(
    val id: Int,
    private val isFreeCampaign: Boolean?,
    private val purchaseUseCoin: Int?,
    private val rentalUseCoin: Int?,
    private val rentalExpirationAt: Long?,
) {
    val isLocked: Boolean
        get() = isFreeCampaign == false && (purchaseUseCoin != 0 || rentalUseCoin != 0) && rentalExpirationAt == null
}

@Serializable
class ViewerResponse(
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
