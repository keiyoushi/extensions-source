package eu.kanade.tachiyomi.extension.ja.mangaone

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class RankingResponseList(
    @ProtoNumber(1) val categories: List<RankingCategory>,
)

@Serializable
class RankingCategory(
    @ProtoNumber(3) val rankingLists: List<RankingList>,
)

@Serializable
class RankingList(
    @ProtoNumber(2) val type: String,
    @ProtoNumber(3) val titles: List<RankingTitle>,
)

@Serializable
class RankingTitle(
    @ProtoNumber(1) val entry: RankingEntry,
)

@Serializable
class RankingEntry(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(6) private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = cover
    }
}

@Serializable
class LatestResponseList(
    @ProtoNumber(1) val list: List<ResponseList>,
)

@Serializable
class ResponseList(
    @ProtoNumber(3) val responseList: List<TitleWrapper>,
)

@Serializable
class TitleWrapper(
    @ProtoNumber(1) val titles: Titles,
)

@Serializable
class Titles(
    @ProtoNumber(1) val entry: Entry,
)

@Serializable
class Entry(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(6) private val banner: String?,
    @ProtoNumber(16) private val cover: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = cover ?: banner
    }
}

@Serializable
class TagResponse(
    @ProtoNumber(2) val tags: List<Tags>?,
)

@Serializable
class Tags(
    @ProtoNumber(1) val tagId: Int,
    @ProtoNumber(2) val name: String,
)

@Serializable
class DetailResponse(
    @ProtoNumber(5) val detailEntry: DetailEntry,
)

@Serializable
class DetailEntry(
    @ProtoNumber(1) val details: Details,
)

@Serializable
class Details(
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(4) private val infoText: String?,
    @ProtoNumber(5) private val authors: String?,
    @ProtoNumber(22) private val latestThumbnail: Thumbnail?,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = authors
        description = infoText
        thumbnail_url = latestThumbnail?.thumbnail
    }
}

@Serializable
class Thumbnail(
    @ProtoNumber(3) val thumbnail: String?,
)

@Serializable
class ChapterResponse(
    @ProtoNumber(1) val chapters: Chapters,
)

@Serializable
class Chapters(
    @ProtoNumber(1) val chapterList: List<ChapterList>,
)

@Serializable
class ChapterList(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val title: String,
    @ProtoNumber(3) private val subName: String?,
    @ProtoNumber(5) private val date: String?,
    @ProtoNumber(16) private val points: Points?,
) {
    val isLocked: Boolean
        get() = points?.shortage != null || points?.life != null || points?.coin != null

    fun toSChapter(titleId: String) = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        val chapterName = if (!subName.isNullOrEmpty()) "$title - $subName" else title
        url = "$id#$titleId"
        name = lock + chapterName
        date_upload = dateFormat.tryParse(date)
    }
}

@Serializable
class Points(
    @ProtoNumber(1) val shortage: Int?,
    @ProtoNumber(2) val life: Int?,
    @ProtoNumber(3) val coin: Int?,
)

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)

@Serializable
class ViewerResponse(
    @ProtoNumber(1) val pages: List<ViewerImages>?,
    @ProtoNumber(3) val key: String,
    @ProtoNumber(4) val iv: String,
)

@Serializable
class ViewerImages(
    @ProtoNumber(1) val page: Images,
)

@Serializable
class Images(
    @ProtoNumber(1) val url: String,
)
