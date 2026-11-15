package eu.kanade.tachiyomi.extension.th.manga168

import eu.kanade.tachiyomi.source.model.Filter

class SortFilter :
    Filter.Select<String>(
        "Sort by",
        arrayOf("Latest updates", "Most popular"),
    )

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "On hiatus"),
    )

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        arrayOf(
            "All",
            "Action ต่อสู้",
            "Adult ผู้ใหญ่",
            "Adventure ผจญภัย",
            "Comedy ตลก",
            "Doujin โดจิน",
            "Drama ดราม่า",
            "Ecchi ลามก",
            "Fantasy แฟนตาซี",
            "Gender Bender",
            "Harem",
            "Hentai",
            "Historical ย้อนยุค",
            "Horror สยองขวัญ",
            "Isekai ต่างโลก",
            "Josei โจเซย์",
            "Manhua มังฮัว",
            "Manhwa มังฮวา",
            "Martial Arts จอมยุทธ์",
            "Mature ผู้ใหญ่",
            "Mystery ลึกลับ",
            "Psychological จิตวิทยา",
            "Romance โรแมนซ์",
            "School Life ชีวิตประจำวัน",
            "Sci-fi ไซ-ไฟ",
            "Seinen เซ็นเน็น",
            "Shoujo โชโจ",
            "Shoujo Ai",
            "Shounen โชเน็น",
            "Shounen Ai",
            "Slice of Life ชีวิตประจำวัน",
            "Smut",
            "Sports",
            "Supernatural เหนือธรรมชาติ",
            "Tragedy โศกนาฏกรรม",
            "Yaoi",
            "Yuri",
        ),
    ) {
    val selectedValue: String?
        get() = if (state == 0) null else values[state]
}
