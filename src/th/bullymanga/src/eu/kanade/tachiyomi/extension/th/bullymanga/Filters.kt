package eu.kanade.tachiyomi.extension.th.bullymanga

import eu.kanade.tachiyomi.source.model.Filter

class Genre(val name: String, val slug: String) {
    override fun toString() = name
}

class GenreFilter : Filter.Select<Genre>("แนว", GENRE_OPTIONS.toTypedArray()) {
    val selected: Genre get() = values[state]
}

val GENRE_OPTIONS = listOf(
    Genre("ทั้งหมด", "all"),
    Genre("อ่านมังงะ", "Manga"),
    Genre("มังงะเกาหลี", "Manhwa"),
    Genre("มังงะจีน", "Manhua"),
    Genre("ต่อสู้", "Action"),
    Genre("ผจญภัย", "Adventure"),
    Genre("ดราม่า", "Drama"),
    Genre("แฟนตาซี", "Fantasy"),
    Genre("จิตวิทยา", "Psychological"),
    Genre("โชเน็น", "Shounen"),
    Genre("เหนือธรรมชาติ", "Supernatural"),
    Genre("ราชันยมทูต", "King-of-hell"),
    Genre("ต่างโลก", "Isekai"),
    Genre("ผู้ใหญ่", "Adult"),
    Genre("ดาร์กแฟนตาซี", "Dark-fantasy"),
    Genre("ระบบ", "System"),
    Genre("จอมยุทธ์", "Murim"),
    Genre("ชีวิตประจำวัน", "life-style"),
    Genre("ตลก", "Funny"),
    Genre("โรแมนซ์", "Romance"),
    Genre("โชโจ", "Shoujo"),
    Genre("โดจิน", "Dojin"),
    Genre("ฮาเร็ม", "Harem"),
    Genre("ย้อนยุค", "Retro"),
    Genre("สยองขวัญ", "Horror"),
    Genre("โจเซย์", "Josei"),
    Genre("เวทมนตร์", "Magician"),
    Genre("ลึกลับ", "Mysterious"),
    Genre("เกิดใหม่", "Reborn"),
    Genre("ล้างแค้น", "Avenge"),
    Genre("ไซ-ไฟ", "Sci-fi"),
    Genre("โศกนาฏกรรม", "Tragedy"),
)
