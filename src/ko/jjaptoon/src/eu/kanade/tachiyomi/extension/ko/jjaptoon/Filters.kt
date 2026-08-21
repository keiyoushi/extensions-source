package eu.kanade.tachiyomi.extension.ko.jjaptoon

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl.Builder

/**
 * Select filter that appends a query param only when a non-"all" value is chosen.
 * Maps to Livewire selectedXxx parameters on the comics list.
 */
abstract class SelectFilter(
    name: String,
    private val param: String,
    private val entries: Array<Pair<String, String>>,
) : Filter.Select<String>(name, entries.map { it.first }.toTypedArray()) {

    fun appendToUrl(builder: Builder) {
        val value = entries[state].second
        if (value != "all") builder.addQueryParameter(param, value)
    }
}

/** Genre / category (selectedCategory). */
class CategoryFilter(useEnglish: Boolean = false) :
    SelectFilter(
        name = if (useEnglish) "Genre" else "장르",
        param = "selectedCategories",
        entries = if (useEnglish) {
            arrayOf(
                "All" to "all",
                "Action" to "1",
                "SF / Fantasy" to "2",
                "Slice of Life" to "3",
                "Romance" to "4",
                "Comedy" to "5",
                "School" to "6",
                "SF" to "7",
                "Story" to "8",
                "Fantasy" to "9",
                "BL / Yuri" to "10",
                "Gag / Comedy" to "11",
                "Romance / Pure Love" to "12",
                "Drama" to "13",
                "Historical" to "14",
                "Sports" to "15",
                "Mystery" to "16",
                "Horror / Thriller" to "17",
                "Adult" to "18",
                "Omnibus" to "19",
                "Episode" to "20",
                "Martial Arts" to "21",
                "Shounen" to "22",
                "Other" to "23",
                "Fantasy (alt)" to "28",
            )
        } else {
            arrayOf(
                "전체" to "all",
                "액션" to "1",
                "SF/판타지" to "2",
                "일상" to "3",
                "로맨스" to "4",
                "개그" to "5",
                "학원" to "6",
                "SF" to "7",
                "스토리" to "8",
                "판타지" to "9",
                "BL/백합" to "10",
                "개그/코미디" to "11",
                "연애/순정" to "12",
                "드라마" to "13",
                "시대극" to "14",
                "스포츠" to "15",
                "추리/미스터리" to "16",
                "공포/스릴러" to "17",
                "성인" to "18",
                "옴니버스" to "19",
                "에피소드" to "20",
                "무협" to "21",
                "소년" to "22",
                "기타" to "23",
                "판타지" to "28",
            )
        },
    )

/** Publication status (selectedStatus). */
class StatusFilter(useEnglish: Boolean = false) :
    SelectFilter(
        name = if (useEnglish) "Status" else "상태",
        param = "selectedStatus",
        entries = if (useEnglish) {
            arrayOf(
                "All" to "all",
                "Ongoing" to "ongoing",
                "Completed" to "completed",
                "Hiatus" to "paused",
            )
        } else {
            arrayOf(
                "전체" to "all",
                "연재" to "ongoing",
                "완결" to "completed",
                "휴재" to "paused",
            )
        },
    )

/** Comic type: general / adult / BL / completed (selectedType). */
class TypeFilter(useEnglish: Boolean = false) :
    SelectFilter(
        name = if (useEnglish) "Type" else "유형",
        param = "selectedType",
        entries = if (useEnglish) {
            arrayOf(
                "All" to "all",
                "General" to "general",
                "Adult" to "adult",
                "BL" to "bl",
                "Completed" to "completed",
            )
        } else {
            arrayOf(
                "전체" to "all",
                "일반" to "general",
                "성인" to "adult",
                "BL" to "bl",
                "완결" to "completed",
            )
        },
    )

/** Publisher / platform (selectedPublisher). */
class PublisherFilter(useEnglish: Boolean = false) :
    SelectFilter(
        name = if (useEnglish) "Publisher" else "출처",
        param = "selectedPublisher",
        entries = arrayOf(
            (if (useEnglish) "All" else "전체") to "all",
            "Naver Webtoon" to "naver",
            "Kakao" to "kakao",
            "Lezhin" to "lezhin",
            "Toomics" to "toomics",
            "Ridi" to "ridi",
            "Toptoon" to "toptoon",
            (if (useEnglish) "Bomtoon" else "봄툰") to "bomtoon",
            (if (useEnglish) "Other" else "기타") to "other",
        ),
    )

/** Update schedule by day of week (selectedSchedule). */
class ScheduleFilter(useEnglish: Boolean = false) :
    SelectFilter(
        name = if (useEnglish) "Schedule" else "연재 요일",
        param = "selectedSchedule",
        entries = if (useEnglish) {
            arrayOf(
                "All" to "all",
                "Monday" to "monday",
                "Tuesday" to "tuesday",
                "Wednesday" to "wednesday",
                "Thursday" to "thursday",
                "Friday" to "friday",
                "Saturday" to "saturday",
                "Sunday" to "sunday",
            )
        } else {
            arrayOf(
                "전체" to "all",
                "월요일" to "monday",
                "화요일" to "tuesday",
                "수요일" to "wednesday",
                "목요일" to "thursday",
                "금요일" to "friday",
                "토요일" to "saturday",
                "일요일" to "sunday",
            )
        },
    )
