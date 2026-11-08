package eu.kanade.tachiyomi.extension.th.dongmanga

import eu.kanade.tachiyomi.source.model.Filter

class CategoryFilter(categories: List<String>) : Filter.Select<String>("หมวดหมู่", arrayOf("ทุกหมวด") + categories) {
    val selected get() = values[state].takeIf { state != 0 }
}

class StatusFilter : Filter.Select<String>("สถานะเรื่อง", arrayOf("ทั้งหมด", "กำลังอัปเดต", "จบแล้ว"))

class SortFilter : Filter.Select<String>("เรียงลำดับ", arrayOf("ยอดอ่าน", "อัปเดตล่าสุด", "คะแนน", "ชื่อ ก-ฮ"))
