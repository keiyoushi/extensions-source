package eu.kanade.tachiyomi.extension.vi.dilib

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    MainCategoriesFilter(),
    SubCategoriesFilter(),
    SortFilter(),
    AuthorFilter(),
)

class MainCategoriesFilter :
    UriPartFilter(
        "Nhóm chính",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Fantasy", "fantasy"),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Supernatural", "supernatural"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Seinen", "seinen"),
            Pair("Drama", "drama"),
            Pair("Mystery", "mystery"),
            Pair("Cooking", "cooking"),
            Pair("Harem", "harem"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Historical", "historical"),
            Pair("Psychological", "psychological"),
            Pair("Tragedy", "tragedy"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Horror", "horror"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Adult (18+)", "adult-18"),
            Pair("Sports", "sports"),
            Pair("Ecchi", "ecchi"),
            Pair("Webtoon", "webtoon"),
            Pair("Mature", "mature"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Vampire", "vampire"),
            Pair("Josei", "josei"),
            Pair("Xuyên Không", "xuyen-khong"),
            Pair("Magic", "magic"),
            Pair("Monsters", "monsters"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Thriller", "thriller"),
        ),
    )

class SubCategoriesFilter :
    UriPartFilter(
        "Nhóm phụ",
        arrayOf(
            Pair("All", ""),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Action", "action"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Fantasy", "fantasy"),
            Pair("Shounen", "shounen"),
            Pair("Shoujo", "shoujo"),
            Pair("Supernatural", "supernatural"),
            Pair("Sci-Fi", "sci-fi"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Seinen", "seinen"),
            Pair("Drama", "drama"),
            Pair("Mystery", "mystery"),
            Pair("Cooking", "cooking"),
            Pair("Harem", "harem"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Historical", "historical"),
            Pair("Psychological", "psychological"),
            Pair("Tragedy", "tragedy"),
            Pair("Truyện Màu", "truyen-mau"),
            Pair("Horror", "horror"),
            Pair("Slice Of Life", "slice-of-life"),
            Pair("Adult (18+)", "adult-18"),
            Pair("Sports", "sports"),
            Pair("Ecchi", "ecchi"),
            Pair("Webtoon", "webtoon"),
            Pair("Mature", "mature"),
            Pair("Tu Tiên", "tu-tien"),
            Pair("Vampire", "vampire"),
            Pair("Josei", "josei"),
            Pair("Xuyên Không", "xuyen-khong"),
            Pair("Magic", "magic"),
            Pair("Monsters", "monsters"),
            Pair("Hệ Thống", "he-thong"),
            Pair("Thriller", "thriller"),
        ),
    )

class SortFilter :
    UriPartFilter(
        "Sắp xếp",
        arrayOf(
            Pair("Lượt Xem Cao -> Thấp", "1"),
            Pair("Lượt Xem Thấp -> Cao", "2"),
            Pair("Thời Lượng Dài -> Ngắn", "3"),
            Pair("Thời Lượng Ngắn -> Dài", "4"),
            Pair("Đang Là Xu Hướng", "5"),
        ),
    )

class AuthorFilter : Filter.Text("Lọc theo tác giả")

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart(): String? = vals[state].second.ifEmpty { null }
}
