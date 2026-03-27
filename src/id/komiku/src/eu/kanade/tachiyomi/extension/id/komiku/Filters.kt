package eu.kanade.tachiyomi.extension.id.komiku

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

open class UriPartFilter(
    name: String,
    private val param: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    vals.map { it.first }.toTypedArray(),
),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = vals[state].second
        if (selected.isNotEmpty()) {
            builder.addQueryParameter(param, selected)
        }
    }
}

class Type : UriPartFilter("Tipe", "tipe", typeList)
class Order : UriPartFilter("Order", "orderby", orderList)
class Genre1 : UriPartFilter("Genre 1", "genre", genreList)
class Genre2 : UriPartFilter("Genre 2", "genre2", genreList)
class Status : UriPartFilter("Status", "status", statusList)

fun getKomikuFilterList() = FilterList(
    Type(),
    Order(),
    Genre1(),
    Genre2(),
    Status(),
)

private val typeList = arrayOf(
    Pair("Semua", ""),
    Pair("Manga", "manga"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
)

private val orderList = arrayOf(
    Pair("Chapter Terbaru", "modified"),
    Pair("Komik Terbaru", "date"),
    Pair("Peringkat", "meta_value_num"),
    Pair("Acak", "rand"),
)

private val genreList = arrayOf(
    Pair("Semua", ""),
    Pair("Academy", "academy"),
    Pair("Action", "action"),
    Pair("Adaptation", "adaptation"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("apocalypse", "apocalypse"),
    Pair("Beasts", "beasts"),
    Pair("Blacksmith", "blacksmith"),
    Pair("Comedy", "comedy"),
    Pair("Comic", "comic"),
    Pair("Cooking", "cooking"),
    Pair("Crime", "crime"),
    Pair("Crossdressing", "crossdressing"),
    Pair("Dark Fantasy", "dark-fantasy"),
    Pair("Demons", "demons"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Ecchi", "ecchi"),
    Pair("Entertainment", "entertainment"),
    Pair("Fantasy", "fantasy"),
    Pair("Game", "game"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Genderswap", "genderswap"),
    Pair("Genius", "genius"),
    Pair("Ghosts", "ghosts"),
    Pair("Gore", "gore"),
    Pair("Gyaru", "gyaru"),
    Pair("Harem", "harem"),
    Pair("Hentai", "hentai"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Isekai", "isekai"),
    Pair("Josei", "josei"),
    Pair("Knight", "knight"),
    Pair("Long Strip", "long-strip"),
    Pair("Magic", "magic"),
    Pair("Magical Girls", "magical-girls"),
    Pair("Manga", "manga"),
    Pair("Mangatoon", "mangatoon"),
    Pair("Manhwa", "manhwa"),
    Pair("Martial Art", "martial-art"),
    Pair("Martial Arts", "martial-arts"),
    Pair("Mature", "mature"),
    Pair("MC Rebirth", "mc-rebirth"),
    Pair("Mecha", "mecha"),
    Pair("Medical", "medical"),
    Pair("Military", "military"),
    Pair("Monster", "monster"),
    Pair("Monster girls", "monster-girls"),
    Pair("Monsters", "monsters"),
    Pair("Murim", "murim"),
    Pair("Music", "music"),
    Pair("Mystery", "mystery"),
    Pair("Office Workers", "office-workers"),
    Pair("One Shot", "one-shot"),
    Pair("Oneshot", "oneshot"),
    Pair("Police", "police"),
    Pair("Psychological", "psychological"),
    Pair("Regression", "regression"),
    Pair("Reincarnation", "reincarnation"),
    Pair("Revenge", "revenge"),
    Pair("Romance", "romance"),
    Pair("School", "school"),
    Pair("School life", "school-life"),
    Pair("Sci-fi", "sci-fi"),
    Pair("Seinen", "seinen"),
    Pair("Sexual Violence", "sexual-violence"),
    Pair("Shotacon", "shotacon"),
    Pair("Shoujo", "shoujo"),
    Pair("Shoujo Ai", "shoujo-ai"),
    Pair("Shoujo(G)", "shoujog"),
    Pair("Shounen", "shounen"),
    Pair("Shounen Ai", "shounen-ai"),
    Pair("Slice of Life", "slice-of-life"),
    Pair("Slow Life", "slow-life"),
    Pair("Smut", "smut"),
    Pair("Sport", "sport"),
    Pair("Sports", "sports"),
    Pair("Strategy", "strategy"),
    Pair("Super Power", "super-power"),
    Pair("Supernatural", "supernatural"),
    Pair("Survival", "survival"),
    Pair("Sword Fight", "sword-fight"),
    Pair("Sword Master", "sword-master"),
    Pair("Swormanship", "swormanship"),
    Pair("System", "system"),
    Pair("Thriller", "thriller"),
    Pair("Tragedy", "tragedy"),
    Pair("Trauma", "trauma"),
    Pair("Vampire", "vampire"),
    Pair("Villainess", "villainess"),
    Pair("Violence", "violence"),
    Pair("Web Comic", "web-comic"),
    Pair("Webtoon", "webtoon"),
    Pair("Webtoons", "webtoons"),
    Pair("Xianxia", "xianxia"),
    Pair("Xuanhuan", "xuanhuan"),
    Pair("Yuri", "yuri"),
)

private val statusList = arrayOf(
    Pair("Semua", ""),
    Pair("Ongoing", "ongoing"),
    Pair("Tamat", "end"),
)
