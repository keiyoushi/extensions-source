package eu.kanade.tachiyomi.extension.ja.zebrack

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

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
        url = if (info.magazineId != null) "${info.magazineId}#1" else info.id.toString()
        title = name
        thumbnail_url = thumbnail
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
        val magazine = if (id.contains("magazineId")) "#1" else ""
        val titleId = id.substringAfter("=")
        url = "$titleId$magazine"
        title = name
        thumbnail_url = thumbnail
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
        url = "$id#1"
        title = name
        thumbnail_url = thumbnail?.thumb
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
        url = "$chapterId/0#$titleId"
        name = lock + chapterName
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
    @ProtoNumber(17) private val purchased: Int?,
    @ProtoNumber(23) private val isFree: Int?,
    @ProtoNumber(101) val session: SessionError?,
) {
    val isLockedVolume: Boolean
        get() = isFree != 1 && purchased != 1

    private val isTrial: Boolean
        get() = purchased != 1

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLockedVolume) "🔒 (Preview) " else ""
        val isTrial = if (isTrial) "1" else "0"
        val trimName = if (title != null) volumeName.replace(title, "").trim() else volumeName
        url = "$chapterId/1#$titleId:$isTrial"
        uploadDate?.let { date_upload = it * 1000L }
        name = lock + "Volume - $trimName"
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
    @ProtoNumber(10) private val magazineId: Int,
    @ProtoNumber(1000) val session: SessionError?,
) {
    val isLockedMagazine: Boolean
        get() = purchased != 1

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLockedMagazine) "🔒 (Preview) " else ""
        val isTrial = if (isLockedMagazine) "1" else "0"
        url = "$magazineId/2#$issueId:$isTrial"
        uploadDate?.let { date_upload = it * 1000L }
        name = lock + title
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
