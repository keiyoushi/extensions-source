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
    Genre("anal", "anal"),
    Genre("Animal girl", "animal-girl"),
    Genre("Art Book", "art-book"),
    Genre("Artist", "artist"),
    Genre("bbm", "bbm"),
    Genre("BDSM", "bdsm"),
    Genre("beach", "beach"),
    Genre("beast", "beast"),
    Genre("big breasts", "big-breasts"),
    Genre("big dick", "big-dick"),
    Genre("big vagina", "big-vagina"),
    Genre("blowjob", "blowjob"),
    Genre("body modifications", "body-modifications"),
    Genre("body writting", "body-writting"),
    Genre("Breast Sucking", "breast-sucking"),
    Genre("Bukkake", "bukkake"),
    Genre("CG", "cg"),
    Genre("Chikan", "chikan"),
    Genre("comic 18+", "comic-18"),
    Genre("condom", "condom"),
    Genre("Cosplay", "cosplay"),
    Genre("Creampie", "creampie"),
    Genre("Đam Mỹ", "dam-my"),
    Genre("defloration", "defloration"),
    Genre("dirty old man", "dirty-old-man"),
    Genre("double penetration", "double-penetration"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drama", "drama"),
    Genre("Elf", "elf"),
    Genre("Fantasy", "fantasy"),
    Genre("Femdom", "femdom"),
    Genre("Fingering", "fingering"),
    Genre("first time", "first-time"),
    Genre("footjob", "footjob"),
    Genre("Foursome", "foursome"),
    Genre("full color", "full-color"),
    Genre("funny", "funny"),
    Genre("Furry", "furry"),
    Genre("Futanari", "futanari"),
    Genre("Gangbang", "gangbang"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Girl love", "girl-love"),
    Genre("glasses", "glasses"),
    Genre("Group", "group"),
    Genre("Handjob", "handjob"),
    Genre("Harem", "harem"),
    Genre("horror", "horror"),
    Genre("Housewife", "housewife"),
    Genre("incest", "incest"),
    Genre("Incomplete", "incomplete"),
    Genre("insect", "insect"),
    Genre("inseki", "inseki"),
    Genre("Kogal", "kogal"),
    Genre("Lếu lều", "leu-leu"),
    Genre("Lingerie", "lingerie"),
    Genre("Loạn luân chị em", "loan-luan-chi-em"),
    Genre("Loli", "loli"),
    Genre("LXHENTAI", "lxhentai"),
    Genre("LXNOVEL", "lxnovel"),
    Genre("maid", "maid"),
    Genre("Manhwa", "manhwa"),
    Genre("Masturbation", "masturbation"),
    Genre("Mature", "mature"),
    Genre("Milf", "milf"),
    Genre("Mind Break", "mind-break"),
    Genre("Mind Control", "mind-control"),
    Genre("monster", "monster"),
    Genre("Monster Girl", "monster-girl"),
    Genre("mother", "mother"),
    Genre("no sex", "no-sex"),
    Genre("NTR", "ntr"),
    Genre("NUN", "nun"),
    Genre("Nurse", "nurse"),
    Genre("office lady", "office-lady"),
    Genre("OneShot", "oneshot"),
    Genre("orgasm denial", "orgasm-denial"),
    Genre("pregnant", "pregnant"),
    Genre("rape", "rape"),
    Genre("Romance", "romance"),
    Genre("SCAT", "scat"),
    Genre("schoolboy outfit", "schoolboy-outfit"),
    Genre("schoolgirl outfit", "schoolgirl-outfit"),
    Genre("Series", "series"),
    Genre("Short", "truyen-ngan"),
    Genre("Shota", "shota"),
    Genre("Slave", "slave"),
    Genre("small breasts", "small-breasts"),
    Genre("socks", "socks"),
    Genre("Sole Female", "sole-female"),
    Genre("Sole Male", "sole-male"),
    Genre("Sport", "sport"),
    Genre("Squirting", "squirting"),
    Genre("story arc", "story-arc"),
    Genre("Succubus", "succubus"),
    Genre("Supernatural", "supernatural"),
    Genre("swimsuit", "swimsuit"),
    Genre("Swinging", "swinging"),
    Genre("Teacher", "teacher"),
    Genre("Tentacle", "tentacle"),
    Genre("Three some", "three-some"),
    Genre("toys", "toys"),
    Genre("Trap", "trap"),
    Genre("tự sướng", "tu-suong"),
    Genre("Ugly Bastard", "ugly-bastard"),
    Genre("uncensored", "uncensored"),
    Genre("Vanilla", "vanilla"),
    Genre("virginity", "virginity"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
