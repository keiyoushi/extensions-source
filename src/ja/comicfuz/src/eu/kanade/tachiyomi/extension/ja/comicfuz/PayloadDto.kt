package eu.kanade.tachiyomi.extension.ja.comicfuz

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import java.util.Calendar

@Serializable
class DeviceInfo(
    @ProtoNumber(3) private val deviceType: DeviceType,
)

enum class DeviceType {
    IOS,
    ANDROID,
    BROWSER,
}

@Serializable
class DayOfWeekRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val dayOfWeek: DayOfWeek,
)

enum class DayOfWeek(private val dayNum: Int) {
    ALL(0),
    MONDAY(1),
    TUESDAY(2),
    WEDNESDAY(3),
    THURSDAY(4),
    FRIDAY(5),
    SATURDAY(6),
    SUNDAY(7),
    ;

    companion object {
        fun today(): DayOfWeek {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val adjustedDayOfWeek = if (dayOfWeek == Calendar.SUNDAY) 7 else dayOfWeek - 1

            return values().first { it.dayNum == adjustedDayOfWeek }
        }
    }
}

@Serializable
class SearchRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val query: String,
    @ProtoNumber(3) private val pageIndexOfMangas: Int,
    @ProtoNumber(4) private val pageIndexOfBooks: Int,
)

@Serializable
class MangaListRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val tagId: Int,
)

@Serializable
class MangaDetailsRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val mangaId: Int,
)

@Serializable
class MangaViewerRequest(
    @ProtoNumber(1) private val deviceInfo: DeviceInfo,
    @ProtoNumber(2) private val chapterId: Int,
    @ProtoNumber(3) private val useTicket: Boolean,
    @ProtoNumber(4) private val consumePoint: UserPoint,
    @ProtoNumber(5) private val viewerMode: ViewerMode,
)

@Serializable
class UserPoint(
    @ProtoNumber(1) private val event: Int,
    @ProtoNumber(2) private val paid: Int,
)

@Serializable
class ViewerMode(
    @ProtoNumber(1) private val imageQuality: ImageQuality,
)

enum class ImageQuality {
    NORMAL,
    HIGH,
}
