package eu.kanade.tachiyomi.extension.vi.yurigarden

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    StatusFilter(),
    SortFilter(),
    GenreFilter(),
    SearchByFilter(),
)

class GenreFilter :
    Filter.Group<CheckBoxFilter>(
        "Thể loại",
        genres.map { CheckBoxFilter(it.first, it.second, false) },
    )

class SearchByFilter :
    Filter.Group<CheckBoxFilter>(
        "Tìm kiếm theo",
        searchByOptions.map { CheckBoxFilter(it.first, it.second, true) },
    )

open class CheckBoxFilter(name: String, val value: String, state: Boolean) : Filter.CheckBox(name, state)

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        statuses.map { it.first }.toTypedArray(),
    ) {
    val slug get() = statuses[state].second
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        sorts.map { it.first }.toTypedArray(),
    ) {
    val slug get() = sorts[state].second
}

private val genres = arrayOf(
    Pair("4-koma", "4-koma"),
    Pair("Action", "action"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Age Gap", "age-gap"),
    Pair("All Girl", "all-girl"),
    Pair("Animals", "animals"),
    Pair("Anthology", "anthology"),
    Pair("Bath", "bath"),
    Pair("BDSM", "bdsm"),
    Pair("Big Boobs", "big-boobs"),
    Pair("Bisexual", "bisexual"),
    Pair("Blackmail", "blackmail"),
    Pair("Cheating", "cheating"),
    Pair("Childhood Friends", "childhood-friends"),
    Pair("Comedy", "comedy"),
    Pair("Coming-of-age", "coming-of-age"),
    Pair("Crime", "crime"),
    Pair("Dark Fantasy", "dark-fantasy"),
    Pair("Dark Skin", "dark-skin"),
    Pair("Demon", "demon"),
    Pair("Disability", "disability"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Drunk", "drunk"),
    Pair("Ecchi / Erotic", "ecchi-erotic"),
    Pair("Fantasy", "fantasy"),
    Pair("Full Colour", "full-colour"),
    Pair("Futanari", "futanari"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Gore", "gore"),
    Pair("Guro", "guro"),
    Pair("Gyaru", "gyaru"),
    Pair("Harem", "harem"),
    Pair("Height Gap", "height-gap"),
    Pair("Het", "het"),
    Pair("Hint", "hint"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Humorous", "humorous"),
    Pair("Incest", "incest"),
    Pair("Insect", "insect"),
    Pair("Isekai", "isekai"),
    Pair("Loli", "loli"),
    Pair("Magical", "magical"),
    Pair("Maid", "maid"),
    Pair("Manga", "manga"),
    Pair("Mangaka", "mangaka"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Masturbate", "masturbate"),
    Pair("Mecha", "mecha"),
    Pair("Military", "military"),
    Pair("Monster / Youkai", "monster-youkai"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Non-fiction", "non-fiction"),
    Pair("NSFW", "nsfw"),
    Pair("NTR", "ntr"),
    Pair("Office Lady", "office-lady"),
    Pair("Omegaverse", "omegaverse"),
    Pair("Oneshot", "oneshot"),
    Pair("Paranormal", "paranormal"),
    Pair("Parody", "parody"),
    Pair("Philosophical", "philosophical"),
    Pair("Polyamory", "polyamory"),
    Pair("Post-Apocalyptic", "post-apocalyptic"),
    Pair("Psychological", "psychological"),
    Pair("Rape", "rape"),
    Pair("Reversal", "reversal"),
    Pair("Romance", "romance"),
    Pair("Rom-Com", "rom-com"),
    Pair("Samurai", "samurai"),
    Pair("School Life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Spy", "spy"),
    Pair("Sports", "sports"),
    Pair("Succubus", "succubus"),
    Pair("Superhero", "superhero"),
    Pair("Supernatural", "supernatural"),
    Pair("Tentacles", "tentacles"),
    Pair("Threesome", "threesome"),
    Pair("Thriller", "thriller"),
    Pair("Tomboy", "tomboy"),
    Pair("Toy", "toy"),
    Pair("Tragedy", "tragedy"),
    Pair("Transgender", "transgender"),
    Pair("Trauma", "trauma"),
    Pair("Triangle", "triangle"),
    Pair("Tsundere", "tsundere"),
    Pair("Twin", "twin"),
    Pair("Underground", "underground"),
    Pair("Vampire", "vampire"),
    Pair("Vanilla", "vanilla"),
    Pair("Villainess", "villainess"),
    Pair("Violence", "violence"),
    Pair("War", "war"),
    Pair("Wholesome", "wholesome"),
    Pair("Witch", "witch"),
    Pair("Wordless", "wordless"),
    Pair("Yandere", "yandere"),
    Pair("Yuri", "yuri"),
    Pair("Zombie", "zombie"),
)

private val statuses = arrayOf(
    Pair("Tất cả", ""),
    Pair("Sắp ra mắt", "upcoming"),
    Pair("Đang tiến hành", "ongoing"),
    Pair("Đã hoàn thành", "completed"),
    Pair("Tạm dừng", "hiatus"),
    Pair("Đã hủy bỏ", "canceled"),
    Pair("Có yêu cầu xóa", "delete-requested"),
    Pair("Có yêu cầu gộp", "merge-requested"),
)

private val sorts = arrayOf(
    Pair("Mới nhất", "newest"),
    Pair("Cũ nhất", "oldest"),
)

private val searchByOptions = arrayOf(
    Pair("Tiêu đề", "title"),
    Pair("Tên khác", "anotherNames"),
    Pair("Tác giả", "authors"),
    Pair("Họa sĩ", "artists"),
    Pair("Mô tả", "description"),
)
