package eu.kanade.tachiyomi.extension.vi.lxhentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SearchTypeFilter(),
    SortFilter(),
    StatusFilter(),
    Filter.Separator(),
    Filter.Header("Thể loại: Nhấn 1 lần để bao gồm, nhấn 2 lần để loại trừ"),
    GenreFilter(getGenreList()),
)

class SearchTypeFilter :
    Filter.Select<String>(
        "Tìm theo",
        arrayOf("Tên truyện", "Tác giả", "Doujinshi"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "artist"
        2 -> "doujinshi"
        else -> "name"
    }
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp",
        arrayOf("Mới cập nhật", "Mới nhất", "Cũ nhất", "Xem nhiều", "A-Z", "Z-A"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "-created_at"
        2 -> "created_at"
        3 -> "-views"
        4 -> "name"
        5 -> "-name"
        else -> "-updated_at"
    }
}

class StatusFilter :
    Filter.Group<StatusOption>(
        "Tình trạng",
        listOf(
            StatusOption("Đang tiến hành", "ongoing", true),
            StatusOption("Hoàn thành", "completed", true),
            StatusOption("Tạm dừng", "paused", true),
        ),
    )

class StatusOption(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

fun StatusFilter.selectedValues(): List<String> = state
    .filter { option -> option.state }
    .map { option -> option.value }

class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Thể loại", genres)

class Genre(name: String, val slug: String) : Filter.TriState(name) {
    override fun toString(): String = name
}

fun GenreFilter.includedValues(): List<String> = state
    .filter { genre -> genre.state == Filter.TriState.STATE_INCLUDE }
    .map { genre -> genre.slug }

fun GenreFilter.excludedValues(): List<String> = state
    .filter { genre -> genre.state == Filter.TriState.STATE_EXCLUDE }
    .map { genre -> genre.slug }

private fun getGenreList(): List<Genre> = listOf(
    Genre("3D", "3d"),
    Genre("Adult", "adult"),
    Genre("Ahegao", "ahegao"),
    Genre("Anal", "anal"),
    Genre("Animal ", "animal-girl"),
    Genre("Art Book", "art-book"),
    Genre("Artist", "artist"),
    Genre("Bbm", "bbm"),
    Genre("BDSM", "bdsm"),
    Genre("Beach", "beach"),
    Genre("Beast", "beast"),
    Genre("Big breasts ", "big-breasts"),
    Genre("Big dick", "big-dick"),
    Genre("Big vagina", "big-vagina"),
    Genre("Blowjob", "blowjob"),
    Genre("Body modifications", "body-modifications"),
    Genre("Breast Sucking", "breast-sucking"),
    Genre("Bukkake", "bukkake"),
    Genre("CG", "cg"),
    Genre("Chikan", "chikan"),
    Genre("Comic 18+", "comic-18+"),
    Genre("Condom", "condom"),
    Genre("Cosplay", "cosplay"),
    Genre("Creampie", "creampie"),
    Genre("Đam mỹ", "dam-my"),
    Genre("Defloration", "defloration"),
    Genre("Dirty old man", "dirty-old-man"),
    Genre("Double", "double-penetration"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drama", "drama"),
    Genre("Elf", "elf"),
    Genre("Fantasy", "fantasy"),
    Genre("Femdom", "femdom"),
    Genre("Fingering", "fingering"),
    Genre("First time", "first-time"),
    Genre("Footjob", "footjob"),
    Genre("Foursome", "foursome"),
    Genre("Full color", "full-color"),
    Genre("Funny", "funny"),
    Genre("Furry", "furry"),
    Genre("Futanari", "futanari"),
    Genre("Gangbang", "gangbang"),
    Genre("Gender bender", "gender-bender"),
    Genre("Girl love", "girl-love"),
    Genre("glasses", "glasses"),
    Genre("Group", "group"),
    Genre("Handjob", "handjob"),
    Genre("Harem", "harem"),
    Genre("Horror", "horror"),
    Genre("Housewife", "housewife"),
    Genre("Incest", "incest"),
    Genre("Incomplete", "incomplete"),
    Genre("Insect", "insect"),
    Genre("Inseki", "inseki"),
    Genre("Kinh dị", "kinh dị"),
    Genre("Kogal", "kogal"),
    Genre("Lếu lều", "leu-leu"),
    Genre("Lingerie", "lingerie"),
    Genre("Loạn luân chị em", "loan-luan-chi-em"),
    Genre("Loli", "loli"),
    Genre("LXHENTAI", "lxhentai"),
    Genre("LXNOVEL", "lxnovel"),
    Genre("Maid", "maid"),
    Genre("Manhwa", "manhwa"),
    Genre("Masturbation", "masturbation"),
    Genre("Mature", "mature"),
    Genre("Milf", "milf"),
    Genre("Mind break", "mind-break"),
    Genre("Mind control", "mind-control"),
    Genre("Monster", "monster"),
    Genre("Monster Girl", "monster-girl"),
    Genre("mother", "mother"),
    Genre("No sex ", "no-sex"),
    Genre("NTR", "ntr"),
    Genre("NUN", "nun"),
    Genre("Nurse", "nurse"),
    Genre("Office", "office-lady"),
    Genre("OneShot", "oneshot"),
    Genre("Orgasm denial", "orgasm-denial"),
    Genre("Pregnant", "pregnant"),
    Genre("Rape", "rape"),
    Genre("SCAT", "scat"),
    Genre("Schoolboy outfit", "schoolboy-outfit"),
    Genre("Schoolgirl outfit", "schoolgirl-outfit"),
    Genre("Series", "series"),
    Genre("Shota", "shota"),
    Genre("Slave", "slave"),
    Genre("Small", "small-breasts"),
    Genre("Socks", "socks"),
    Genre("Sole female", "sole-female"),
    Genre("Sole male", "sole-male"),
    Genre("Sport", "sport"),
    Genre("Squirting", "squirting"),
    Genre("Story arc", "story-arc"),
    Genre("Succubus", "succubus"),
    Genre("Supernatural", "supernatural"),
    Genre("swimsuit", "swimsuit"),
    Genre("Swinging", "swinging"),
    Genre("Teacher", "teacher"),
    Genre("Three some", "three-some"),
    Genre("Toys", "toys"),
    Genre("Trap", "trap"),
    Genre("Truyện ngắn", "truyen-ngan"),
    Genre("Tự sướng", "tu-suong"),
    Genre("Uncensored", "uncensored"),
    Genre("Vanilla", "vanilla"),
    Genre("virginity", "virginity"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
