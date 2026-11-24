package eu.kanade.tachiyomi.extension.th.readtoon

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : Filter.Sort("เรียงตาม", SORTS.map { it.first }.toTypedArray(), Selection(0, false)) {
    val sort get() = SORTS[state?.index ?: 0].second
    val order get() = if (state?.ascending == true) "asc" else "desc"
}

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilter : Filter.Group<GenreCheckBox>("หมวดหมู่", GENRES.map { GenreCheckBox(it.first, it.second) }) {
    val selected get() = state.filter { it.state }.map { it.slug }
}

fun getFilters() = FilterList(
    Filter.Header("คำค้นหาเรียงตามความเกี่ยวข้อง"),
    SortFilter(),
    GenreFilter(),
)

private val SORTS = listOf(
    "อัปเดตล่าสุด" to "chapterAt",
    "เรื่องใหม่" to "createdAt",
    "ชื่อเรื่อง" to "title",
)

// The site's genres that list anything to guests
private val GENRES = listOf(
    "Action แอ็คชั่น" to "action",
    "Adventure ผจญภัย" to "adventure",
    "Comedy ตลก" to "comedy",
    "Drama ดราม่า" to "drama",
    "Ecchi ลามก" to "ecchi",
    "Fantasy แฟนตาซี" to "fantasy",
    "Historical ประวัติศาสตร์" to "historical",
    "Horror สยองขวัญ" to "horror",
    "Isekai ต่างโลก" to "isekai",
    "Manga มังงะญี่ปุ่น" to "manga",
    "Manhua มังงะจีน" to "manhua",
    "Manhwa มังงะเกาหลี" to "manhwa",
    "Martial Arts แนวต่อสู้" to "martial-arts",
    "Office ออฟฟิศ" to "office",
    "One Shot มังงะสั้น" to "one-shot",
    "Psychological จิตวิทยา" to "psychological",
    "Romance โรแมนติก" to "romance",
    "School Life ชีวิตในโรงเรียน" to "school-life",
    "Sci-fi ไซไฟ" to "sci-fi",
    "Shounen โชเน็น" to "shounen",
    "Smut แฟนฟิค" to "smut",
    "Sports กีฬา" to "sports",
    "Supernatural สิ่งเหนือธรรมชาติ" to "supernatural",
    "System แนวระบบ" to "system",
    "Tensei เกิดใหม่" to "tensei",
    "Yaoi มังงะวาย" to "yaoi",
    "Yuri ยูริ" to "yuri",
    "ภาพขาวดำ" to "uncolored",
    "มังงะแนะนำ" to "recommended-manga",
    "สลับเพศ" to "gender-swap",
)
