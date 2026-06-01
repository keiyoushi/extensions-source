package eu.kanade.tachiyomi.extension.vi.truyenmm

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class GenreFilter :
    Filter.Select<String>(
        "Thể loại",
        GENRE_VALUES.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart(): String = GENRE_VALUES[state].second
}

fun getFilters(): FilterList = FilterList(
    Filter.Header("Khi tìm kiếm bộ lọc sẽ bị bỏ qua"),
    GenreFilter(),
)

private val GENRE_VALUES = arrayOf(
    Pair("18+", "18"),
    Pair("Adult", "adult"),
    Pair("Big Boobs", "big-boobs"),
    Pair("Boy Love", "boy-love"),
    Pair("Cheating", "cheating"),
    Pair("Comedy", "comedy"),
    Pair("Cosplay", "cosplay"),
    Pair("Dirty Old Man", "dirty-old-man"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Fantasy", "fantasy"),
    Pair("Full Color", "full-color"),
    Pair("Furry", "furry"),
    Pair("Game", "game"),
    Pair("GangBang", "gangbang"),
    Pair("Guro", "guro"),
    Pair("Harem", "harem"),
    Pair("Isekai", "isekai"),
    Pair("Không che", "khong-che"),
    Pair("Kinh Dị", "kinh-di"),
    Pair("Loli", "loli"),
    Pair("Loạn Luân", "loan-luan"),
    Pair("Manga", "manga"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Milf", "milf"),
    Pair("Mind Break", "mind-break"),
    Pair("NTR", "ntr"),
    Pair("Netori", "netori"),
    Pair("Ngôn Tình", "ngon-tinh"),
    Pair("OneShot", "oneshot"),
    Pair("Rape", "rape"),
    Pair("Romance", "romance"),
    Pair("School Life", "school-life"),
    Pair("SchoolGirl", "schoolgirl"),
    Pair("Seinen", "seinen"),
    Pair("Shota", "shota"),
    Pair("Shoujo ai", "shoujo-ai"),
    Pair("Shoujo", "shoujo"),
    Pair("Shounen ai", "shounen-ai"),
    Pair("Siscon", "siscon"),
    Pair("Slice Of Life", "slice-of-life"),
    Pair("Tragedy", "tragedy"),
    Pair("Tsundere", "tsundere"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
)
