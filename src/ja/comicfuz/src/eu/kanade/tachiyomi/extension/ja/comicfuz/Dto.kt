package eu.kanade.tachiyomi.extension.ja.comicfuz

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class DeviceInfo(
    @ProtoNumber(3) private val deviceType: Int,
)

@Serializable
class DayOfWeekRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val dayOfWeek: Int,
)

@Serializable
class DayOfWeekResponse(
    @ProtoNumber(1) val mangas: List<Manga>,
)

@Serializable
class Manga(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(4) val cover: String,
    @ProtoNumber(14) val description: String,
)

@Serializable
class MangaDetailsRequest(
    @ProtoNumber(1) val deviceInfo: DeviceInfo,
    @ProtoNumber(2) val mangaId: Int,
)

@Serializable
class MangaDetailsResponse(
    @ProtoNumber(2) val manga: Manga,
    @ProtoNumber(3) val chapters: List<ChapterGroup>,
    @ProtoNumber(4) val authors: List<Author>,
    @ProtoNumber(7) val tags: List<Name>,
)

@Serializable
class Author(
    @ProtoNumber(1) val author: Name,
)

@Serializable
class Name(
    @ProtoNumber(2) val name: String,
)

@Serializable
class ChapterGroup(
    @ProtoNumber(2) val chapters: List<Chapter>,
)

@Serializable
class Chapter(
    @ProtoNumber(1) val id: Int,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(5) val points: Point,
    @ProtoNumber(8) private val date: String = "",
) {
    val timestamp get() = try {
        dateFormat.parse(date)!!.time
    } catch (_: ParseException) {
        0L
    }
}

private val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)

@Serializable
class Point(
    @ProtoNumber(2) ammount = 0
)

@Serializable
class MangaViewerRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val chapterId: Int,
    @ProtoNumber(3) private val useTicket: Boolean,
    @ProtoNumber(4) private val consumePoint: UserPoint,
    // @ProtoNumber(5) private val viewerMode: ViewerMode
)

@Serializable
class UserPoint(
    @ProtoNumber(1) private val event: Int,
    @ProtoNumber(2) private val paid: Int,
)

@Serializable
class ViewerMode(
    @ProtoNumber(1) private val imageQuality: Int,
)

@Serializable
class MangaViewerResponse(
    @ProtoNumber(3) val pages: List<ViewerPage>,
)

@Serializable
class ViewerPage(
    @ProtoNumber(1) val image: Image? = null,
)

@Serializable
class Image(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(3) val iv: String = "",
    @ProtoNumber(4) val encryptionKey: String = "",
    @ProtoNumber(7) val isExtraPage: Boolean = false,
)
