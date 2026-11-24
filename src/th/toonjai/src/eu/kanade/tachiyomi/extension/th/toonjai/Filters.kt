package eu.kanade.tachiyomi.extension.th.toonjai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class CategoryFilter :
    Filter.Select<String>(
        "หมวดหมู่",
        CATEGORIES.map { it.first }.toTypedArray(),
    ) {
    val slug get() = CATEGORIES[state].second

    companion object {
        private val CATEGORIES = listOf(
            "ทั้งหมด" to "",
            "ต่อสู้" to "action",
            "ผู้ใหญ่" to "adult",
            "ผจญภัย" to "adventure",
            "ตลก" to "comedy",
            "โดจิน" to "doujin",
            "ดราม่า" to "drama",
            "ลามก" to "ecchi",
            "แฟนตาซี" to "fantasy",
            "ต่างโลก" to "isekai",
            "มังงะญี่ปุ่น" to "manga",
            "มังงะจีน" to "manhua",
            "มังงะเกาหลี" to "manhwa",
            "มังงะแนะนำ" to "recommends",
            "โรแมนติก" to "romance",
            "ไซไฟ" to "sci-fi",
            "เกิดใหม่" to "tensei",
            "มังงะวาย" to "yaoi",
        )
    }
}

fun getFilters() = FilterList(CategoryFilter())
