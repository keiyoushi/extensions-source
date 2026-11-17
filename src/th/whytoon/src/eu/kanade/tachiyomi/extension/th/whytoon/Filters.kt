package eu.kanade.tachiyomi.extension.th.whytoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class SortFilter :
    SelectFilter(
        "เรียงตาม",
        listOf(
            "ค่าเริ่มต้น" to null,
            "ล่าสุด" to "recent",
            "ยอดนิยม" to "popularity",
            "คะแนนสูง" to "rating",
            "A-Z" to "alphabetical",
        ),
    )

class GenreFilter :
    SelectFilter(
        "หมวดหมู่",
        listOf(
            "ทั้งหมด" to null,
            "Action - แอ็คชั่น" to "action",
            "Adult - ผู้ใหญ่" to "adult",
            "Adventure - ผจญภัย" to "adventure",
            "Comedy - ตลก" to "comedy",
            "Drama - ดราม่า" to "drama",
            "Ecchi - ลามก" to "ecchi",
            "Fantasy - แฟนตาซี" to "fantasy",
            "Isekai - อิเซไก" to "isekai",
            "Manga - มังงะญี่ปุ่น" to "manga",
            "Manhua - มังงะจีน" to "manhua",
            "Manhwa - มังงะเกาหลี" to "manhwa",
            "Recommend - มังงะแนะนำ" to "recommends",
            "Romance - โรแมนซ์" to "romance",
            "Sci-Fi - ไซไฟ" to "sci-fi",
            "Tensei - เกิดใหม่" to "tensei",
            "Yaoi - วาย / BL" to "yaoi",
        ),
    )

class StatusFilter :
    SelectFilter(
        "สถานะ",
        listOf(
            "ทั้งหมด" to null,
            "กำลังอัปเดต" to "ONGOING",
            "จบแล้ว" to "COMPLETED",
        ),
    )

class CountryFilter :
    SelectFilter(
        "ประเทศ",
        listOf(
            "ทั้งหมด" to null,
            "ญี่ปุ่น" to "jp",
            "เกาหลี" to "kr",
            "จีน" to "cn",
            "ไทย" to "th",
        ),
    )

class AgeRatingFilter :
    SelectFilter(
        "เรทอายุ",
        listOf(
            "ทั้งหมด" to null,
            "G" to "G",
            "PG" to "PG",
            "R" to "R",
            "NC-17" to "NC",
        ),
    )

fun getFilters() = FilterList(SortFilter(), GenreFilter(), StatusFilter(), CountryFilter(), AgeRatingFilter())
