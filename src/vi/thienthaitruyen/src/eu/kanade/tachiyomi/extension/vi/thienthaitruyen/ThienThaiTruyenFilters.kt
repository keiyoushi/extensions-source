package eu.kanade.tachiyomi.extension.vi.thienthaitruyen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    GenreFilter(),
    StatusFilter(),
    SortFilter(),
)

class UriPart(
    val displayName: String,
    val value: String?,
)

open class UriPartFilter(
    name: String,
    values: Array<UriPart>,
) : Filter.Select<String>(name, values.map(UriPart::displayName).toTypedArray()) {
    private val entries = values

    val selected: String?
        get() = entries[state].value
}

class GenreFilter :
    UriPartFilter(
        "Thể loại",
        arrayOf(
            UriPart("All", null),
            UriPart("Adult", "adult"),
            UriPart("Ecchi", "ecchi"),
            UriPart("Harem", "harem"),
            UriPart("Hentai", "hentai"),
            UriPart("Manhwa", "manhwa"),
            UriPart("Smut", "smut"),
            UriPart("Truyện Tranh 18+", "truyen-tranh-18"),
            UriPart("Webtoon", "webtoon"),
            UriPart("Manhua", "manhua"),
            UriPart("Mature", "mature"),
            UriPart("Manga", "manga"),
            UriPart("Thiên Thai", "thien-thai"),
            UriPart("Ntr", "ntr"),
            UriPart("Ngực Lớn", "nguc-lon"),
            UriPart("Milf", "milf"),
            UriPart("Đam Mỹ", "dam-my"),
            UriPart("Manhwa 18+", "manhwa-18"),
            UriPart("Yuri", "yuri"),
            UriPart("Yaoi", "yaoi"),
            UriPart("Big Ass", "big-ass"),
            UriPart("Blowjob", "blowjob"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Trạng thái",
        arrayOf(
            UriPart("All", "all"),
            UriPart("Hoàn thành", "completed"),
            UriPart("Đang ra", "ongoing"),
            UriPart("Đang chờ xử lý", "pending"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp theo",
        arrayOf(
            UriPart("Cập nhật gần đây", "latest"),
            UriPart("Xếp hạng", "rating"),
            UriPart("Số lượng đánh dấu", "bookmark"),
            UriPart("Tên (A-Z)", "name_asc"),
            UriPart("Tên (Z-A)", "name_desc"),
        ),
    )
