package eu.kanade.tachiyomi.extension.th.readrealm

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    val value get() = options[state].second
}

class SortFilter(state: Int = POPULAR) :
    SelectFilter(
        "เรียงตาม",
        listOf(
            "ผู้เข้าชมสูงสุด" to "popular",
            "อัปเดตล่าสุด" to "update",
        ),
        state,
    ) {
    companion object {
        const val POPULAR = 0
        const val UPDATE = 1
    }
}

class CategoryFilter : SelectFilter("หมวดหมู่", listOf("ทุกหมวด" to "") + CATEGORIES)

class StatusFilter :
    SelectFilter(
        "สถานะ",
        listOf(
            "ทั้งหมด" to "",
            "จบแล้ว" to "ended",
            "ยังไม่จบ" to "continue",
        ),
    )

class RatingFilter :
    SelectFilter(
        "เรตติ้ง",
        listOf(
            "ทั้งหมด" to "",
            "ทั่วไป (PG)" to "PG",
            "18+ ขึ้นไป (NC)" to "NC",
        ),
    )

// The site's category table; books store the index
val CATEGORIES = listOf(
    "รักโรแมนติก",
    "ดราม่า",
    "ตลก,คอมเมดี้",
    "แฟนตาซี",
    "เกมออนไลน์",
    "กำลังภายใน",
    "แอคชั่น",
    "ผจญภัย",
    "อดีต,อนาคต",
    "โบราณ,ย้อนยุค",
    "ไซไฟ,วิทยาศาสตร์",
    "ระทึกขวัญ",
    "สืบสวน",
    "สะท้อนชีวิต",
    "แฟนฟิค (FanFiction)",
    "วาย (Yaoi)",
    "ยูริ (Yuri)",
    "ฮาเร็ม",
    "ต่างโลก",
    "ชีวิตในโรงเรียน",
    "เกิดใหม่",
    "ระบบ",
).mapIndexed { index, name -> name to index.toString() }
