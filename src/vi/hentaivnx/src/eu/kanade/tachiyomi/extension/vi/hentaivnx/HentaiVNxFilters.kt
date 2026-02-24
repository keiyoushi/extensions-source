package eu.kanade.tachiyomi.extension.vi.hentaivnx

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class Genre(name: String, val genre: String) : Filter.TriState(name) {
    override fun toString() = name
}

class TextField : Filter.Text("Tên thể loại có chứa", "")

class SortByList(state: Int = 0) :
    Filter.Select<Genre>(
        "Sắp xếp theo",
        arrayOf(
            Genre("Truyện mới", "15"),
            Genre("Top all", "10"),
            Genre("Top tháng", "11"),
            Genre("Top tuần", "12"),
            Genre("Top ngày", "13"),
            Genre("Theo dõi", "20"),
            Genre("Bình luận", "25"),
            Genre("Số chapter", "30"),
            Genre("Top Follow", "19"),
        ),
        state,
    )

class ChapterCountList :
    Filter.Select<Genre>(
        "Số lượng chapter",
        arrayOf(
            Genre(">= 0 chapters", "0"),
            Genre(">= 5 chapters", "5"),
            Genre(">= 10 chapters", "10"),
            Genre(">= 20 chapters", "20"),
            Genre(">= 30 chapters", "30"),
            Genre(">= 50 chapters", "50"),
        ),
    )

fun getGenreList() = listOf(
    Genre("Adult", "25"),
    Genre("Ahegao", "1"),
    Genre("Anal", "104"),
    Genre("Big Ass", "138"),
    Genre("Big Boobs", "46"),
    Genre("Big Breasts", "293"),
    Genre("Big Penis", "144"),
    Genre("Blowjob", "216"),
    Genre("Blowjobs", "47"),
    Genre("Cheating", "8"),
    Genre("Chơi Hai Lỗ", "37"),
    Genre("Có Che", "145"),
    Genre("Condom", "136"),
    Genre("Cosplay", "54"),
    Genre("Doujinshi", "22"),
    Genre("Drama", "26"),
    Genre("Ecchi", "29"),
    Genre("Elf", "162"),
    Genre("Fantasy", "163"),
    Genre("Femdom", "58"),
    Genre("Fingering", "172"),
    Genre("Full Color", "55"),
    Genre("Gangbang", "59"),
    Genre("Glasses", "9"),
    Genre("Group", "38"),
    Genre("Hãm Hiếp", "42"),
    Genre("Handjob", "175"),
    Genre("Harem", "21"),
    Genre("Hậu Môn", "34"),
    Genre("Housewife", "16"),
    Genre("Huge Ass", "114"),
    Genre("Huge Boobs", "60"),
    Genre("Huge Dick", "218"),
    Genre("Incest", "176"),
    Genre("Không Che", "11"),
    Genre("Loạn Luân", "43"),
    Genre("Loli", "44"),
    Genre("Manga", "89"),
    Genre("Manhwa", "90"),
    Genre("Masturbation", "109"),
    Genre("Milf", "40"),
    Genre("Mind Break", "24"),
    Genre("Mind Control", "23"),
    Genre("Mother", "128"),
    Genre("Nakadashi", "48"),
    Genre("Ngực Lớn", "31"),
    Genre("Ngực Nhỏ", "36"),
    Genre("Ntr", "17"),
    Genre("Nữ Sinh", "35"),
    Genre("Oneshot", "33"),
    Genre("Paizuri", "108"),
    Genre("Rape", "rape"),
    Genre("Romance", "romance"),
    Genre("School Uniform", "27"),
    Genre("Schoolgirl", "52"),
    Genre("Schoolgirl Outfit", "295"),
    Genre("Series", "32"),
    Genre("Sex Toys", "18"),
    Genre("Shota", "49"),
    Genre("Small Breasts", "412"),
    Genre("Smut", "28"),
    Genre("Stocking", "97"),
    Genre("Stockings", "106"),
    Genre("Truyện Không Che", "174"),
    Genre("Truyện Màu", "7"),
    Genre("Truyện Ngắn", "4"),
    Genre("Uncensored", "96"),
    Genre("Vanilla", "98"),
    Genre("Vếu To", "5"),
    Genre("Virgin", "45"),
    Genre("X-ray", "203"),
    Genre("Yuri", "197"),
)
