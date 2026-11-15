package eu.kanade.tachiyomi.extension.ja.zebrack

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.protobuf.ProtoNumber

private const val TYPE_TITLE = "title"
const val TYPE_MAGAZINE = "magazine"
const val TYPE_CHAPTER = "chapter"
const val TYPE_VOLUME = "volume"
private const val TYPE_ISSUE = "issue"

@Serializable
class RankingResponse(
    @ProtoNumber(1) val list: List<TitleRanking>,
)

@Serializable
class TitleRanking(
    @ProtoNumber(1) val type: String,
    @ProtoNumber(2) val titles: List<RankingEntries>,
)

@Serializable
class RankingEntries(
    @ProtoNumber(1) private val thumbnail: String?,
    @ProtoNumber(3) private val name: String,
    @ProtoNumber(11) private val info: RankingInfo,
) {
    fun toSManga() = SManga.create().apply {
        url = (info.magazineId ?: info.id).toString()
        title = name
        thumbnail_url = thumbnail
        memo = buildJsonObject {
            put("type", if (info.magazineId != null) TYPE_MAGAZINE else TYPE_TITLE)
        }
    }
}

@Serializable
class RankingInfo(
    @ProtoNumber(5) val id: Int?,
    @ProtoNumber(7) val magazineId: Int?, // if "7": Magazine -> "5" is missing
)

@Serializable
class LatestResponse(
    @ProtoNumber(1) val list: List<LatestEntries>,
)

@Serializable
class LatestEntries(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(6) private val thumbnail: String?,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnail
        memo = buildJsonObject {
            put("type", TYPE_TITLE)
        }
    }
}

@Serializable
class SearchResponse(
    @ProtoNumber(1) val list: List<SearchEntries>, // "2": Novels
)

@Serializable
class SearchEntries(
    @ProtoNumber(1) private val thumbnail: String?,
    @ProtoNumber(2) private val id: String,
    @ProtoNumber(3) private val name: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.substringAfter("=")
        title = name
        thumbnail_url = thumbnail
        memo = buildJsonObject {
            put("type", if (id.contains("magazineId")) TYPE_MAGAZINE else TYPE_TITLE)
        }
    }
}

@Serializable
class MagazineFilterResponse(
    @ProtoNumber(50) val magazines: MagazineList,
)

@Serializable
class MagazineList(
    @ProtoNumber(1) val magazinesListAll: List<MagazineEntries>,
    @ProtoNumber(3) val magazinesListWoman: List<MagazineEntries>,
    @ProtoNumber(4) val magazinesListMen: List<MagazineEntries>,

)

@Serializable
class MagazineEntries(
    @ProtoNumber(1) private val id: Int,
    @ProtoNumber(2) private val thumbnail: MagazineEntryThumbnail?,
    @ProtoNumber(6) private val name: String,
) {
    fun toSManga() = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnail?.thumb
        memo = buildJsonObject {
            put("type", TYPE_MAGAZINE)
        }
    }
}

@Serializable
class MagazineEntryThumbnail(
    @ProtoNumber(1) val thumb: String?,
)

@Serializable
class MangaDetailsResponse(
    @ProtoNumber(21) val details: MangaDetails,
)

@Serializable
class MangaDetails(
    @ProtoNumber(2) private val name: String,
    @ProtoNumber(3) private val authors: String?,
    @ProtoNumber(11) private val thumbnail: Thumbnail?,
    @ProtoNumber(20) private val update: String?,
    @ProtoNumber(103) private val publisher: String?,
    @ProtoNumber(203) private val info: Info?,

) {
    fun toSManga() = SManga.create().apply {
        title = name
        author = authors?.replace(",", ", ")
        description = buildString {
            info?.text?.let { append(it) }

            if (!publisher.isNullOrBlank()) {
                append("\n\nPublisher/Label: $publisher ")
            }
        }
        genre = info?.genres?.mapNotNull { it.genreName }?.joinToString()
        status = if (update != null) SManga.ONGOING else SManga.UNKNOWN
        thumbnail_url = thumbnail?.portrait
        memo = buildJsonObject {
            put("type", TYPE_TITLE)
        }
    }
}

@Serializable
class Thumbnail(
    @ProtoNumber(21) val portrait: String?,
)

@Serializable
class Info(
    @ProtoNumber(2) val text: String?,
    @ProtoNumber(5) val genres: List<Genres>?,
)

@Serializable
class Genres(
    @ProtoNumber(2) val genreName: String?,
)

@Serializable
class MagazineDetailsResponse(
    @ProtoNumber(3) val details: MagazineDetails,
)

@Serializable
class MagazineDetails(
    @ProtoNumber(2) private val magazineThumbnail: MagazineThumbnail?,
    @ProtoNumber(5) private val update: String?,
    @ProtoNumber(6) private val name: String,
) {
    fun toSManga() = SManga.create().apply {
        title = name
        description = update
        thumbnail_url = magazineThumbnail?.thumbnail
        memo = buildJsonObject {
            put("type", TYPE_MAGAZINE)
        }
    }
}

@Serializable
class MagazineThumbnail(
    @ProtoNumber(1) val thumbnail: String?,
)

@Serializable
class ChapterResponse(
    @ProtoNumber(4) val chapterList: List<ChapterList>?,
)

@Serializable
class ChapterList(
    @ProtoNumber(3) val chapters: List<Chapter>?,
)

@Serializable
class Chapter(
    @ProtoNumber(1) private val chapterId: Int,
    @ProtoNumber(2) private val titleId: Int,
    @ProtoNumber(3) private val chapterName: String,
    @ProtoNumber(11) private val purchased: Int?, // 1 = purchased/rented, 4 = locked
    @ProtoNumber(12) private val price: Int?,
    @ProtoNumber(1000) val session: SessionError?,
) {
    val isLocked: Boolean
        get() = price != null && price > 0 && (purchased != null && purchased != 1)
    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = chapterId.toString()
        name = lock + chapterName
        memo = buildJsonObject {
            put("type", TYPE_CHAPTER)
            put("titleId", titleId.toString())
        }
    }
}

@Serializable
class VolumeResponse(
    @ProtoNumber(100) val volumeData: VolumeData?,
)

@Serializable
class VolumeData(
    @ProtoNumber(2) val volumeList: List<Volume>?,
)

@Serializable
class Volume(
    @ProtoNumber(2) private val titleId: Int,
    @ProtoNumber(3) private val chapterId: Int,
    @ProtoNumber(4) private val title: String?,
    @ProtoNumber(5) private val volumeName: String,
    @ProtoNumber(7) private val uploadDate: Long?,
    @ProtoNumber(12) private val trialable: Int?,
    @ProtoNumber(17) private val purchased: Int?,
    @ProtoNumber(23) private val isFree: Int?,
    @ProtoNumber(101) val session: SessionError?,
) {
    val isLockedVolume: Boolean
        get() = isFree != 1 && purchased != 1

    private val isTrial: Boolean
        get() = purchased != 1 && trialable == 1

    fun toSChapter() = SChapter.create().apply {
        val trial = if (isTrial && isFree != 1) "(Preview) " else ""
        val lock = if (isLockedVolume) "🔒 " else ""
        val trimName = if (title != null) volumeName.replace(title, "").trim() else volumeName
        url = chapterId.toString()
        uploadDate?.let { date_upload = it * 1000L }
        name = lock + trial + "Volume - $trimName"
        memo = buildJsonObject {
            put("type", TYPE_VOLUME)
            put("titleId", titleId.toString())
            put("trial", isTrial)
        }
    }
}

@Serializable
class MagazineResponse(
    @ProtoNumber(52) val magazineData: MagazineData?,
)

@Serializable
class MagazineData(
    @ProtoNumber(3) val magazineList: List<Magazine>?,
)

@Serializable
class Magazine(
    @ProtoNumber(1) private val issueId: Int,
    @ProtoNumber(4) private val title: String,
    @ProtoNumber(6) private val uploadDate: Long?,
    @ProtoNumber(8) private val purchased: Int?,
    @ProtoNumber(9) private val trialable: Int?,
    @ProtoNumber(10) private val magazineId: Int,
    @ProtoNumber(1000) val session: SessionError?,
) {
    val isLockedMagazine: Boolean
        get() = purchased != 1

    private val isTrial: Boolean
        get() = purchased != 1 && trialable == 1

    fun toSChapter() = SChapter.create().apply {
        val trial = if (isTrial) "(Preview) " else ""
        val lock = if (isLockedMagazine) "🔒 " else ""
        url = issueId.toString()
        uploadDate?.let { date_upload = it * 1000L }
        name = lock + trial + title
        memo = buildJsonObject {
            put("type", TYPE_ISSUE)
            put("magazineId", magazineId.toString())
            put("trial", isLockedMagazine)
        }
    }
}

@Serializable
class ViewerResponse(
    @ProtoNumber(1) val images: List<ViewerImages>,
    @ProtoNumber(101) val session: SessionError?,
)

@Serializable
class ViewerImages(
    @ProtoNumber(1) val pages: Pages?,
)

@Serializable
class Pages(
    @ProtoNumber(1) val page: String?,
    @ProtoNumber(2) val key: String?,
)

@Serializable
class MagazineViewerImages(
    @ProtoNumber(32) val pages: MagazinePageList?,
    @ProtoNumber(1000) val session: SessionError?,
)

@Serializable
class MagazinePageList(
    @ProtoNumber(1) val pagesList: List<MagazinePages>?,
)

@Serializable
class MagazinePages(
    @ProtoNumber(1) val page: String?,
    @ProtoNumber(3) val key: String?,
)

@Serializable
class SessionError(
    @ProtoNumber(1) val message: String?,
)
