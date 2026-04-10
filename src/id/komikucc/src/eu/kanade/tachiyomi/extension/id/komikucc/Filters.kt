package eu.kanade.tachiyomi.extension.id.komikucc

import eu.kanade.tachiyomi.source.model.Filter
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

class StatusFilter : UriPartFilter("Status", "status", statusList)
class TypeFilter : UriPartFilter("Type", "type", typeList)
class OrderFilter : UriPartFilter("Order", "order", orderList)

private val statusList = arrayOf(
    "Semua" to "",
    "Ongoing" to "ongoing",
    "Selesai" to "completed",
    "Hiatus" to "hiatus",
)

private val typeList = arrayOf(
    "Semua" to "",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Manhua" to "manhua",
)

private val orderList = arrayOf(
    "Semua" to "",
    "Popular" to "popular",
    "Update" to "update",
    "New" to "latest",
    "A-Z" to "title",
    "Z-A" to "titlereverse",
)

class Genre(name: String, val value: String) : Filter.CheckBox(name)

class GenreList :
    Filter.Group<Genre>(
        "Genres",
        genreList.map { Genre(it.first, it.second) },
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter("genres[]", it.value)
        }
    }
}

private val genreList = arrayOf(
    "4-Koma" to "4-koma",
    "Action" to "action",
    "Action Adventure" to "action-adventure",
    "Adaptation" to "adaptation",
    "Adult" to "adult",
    "Adventure" to "adventure",
    "Animals" to "animals",
    "Anthology" to "anthology",
    "Antihero" to "antihero",
    "apocalypse" to "apocalypse",
    "Award Winning" to "award-winning",
    "Beasts" to "beasts",
    "Bodyswap" to "bodyswap",
    "Boys' Love" to "boys-love",
    "Bully" to "bully",
    "Cartoon" to "cartoon",
    "Childhood Friends" to "childhood-friends",
    "Comedy" to "comedy",
    "Comic" to "comic",
    "Cooking" to "cooking",
    "Crime" to "crime",
    "Crossdressing" to "crossdressing",
    "Dance" to "dance",
    "Dark Fantasy" to "dark-fantasy",
    "Delinquent" to "delinquent",
    "Delinquents" to "delinquents",
    "Dementia" to "dementia",
    "Demon" to "demon",
    "Demons" to "demons",
    "Doujinshi" to "doujinshi",
    "Drama" to "drama",
    "Dungeons" to "dungeons",
    "Ecchi" to "ecchi",
    "Emperor's daughter" to "emperors-daughter",
    "Entertainment" to "entertainment",
    "Fan-Colored" to "fan-colored",
    "Fantas" to "fantas",
    "Fantasy" to "fantasy",
    "Fetish" to "fetish",
    "Full Color" to "full-color",
    "Game" to "game",
    "Games" to "games",
    "Gang" to "gang",
    "Gender Bender" to "gender-bender",
    "Genderswap" to "genderswap",
    "Ghosts" to "ghosts",
    "Girls" to "girls",
    "Girls' Love" to "girls-love",
    "gore" to "gore",
    "gorre" to "gorre",
    "Gyaru" to "gyaru",
    "Harem" to "harem",
    "Hentai" to "hentai",
    "Hero" to "hero",
    "Historical" to "historical",
    "Horror" to "horror",
    "Imageset" to "imageset",
    "Incest" to "incest",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Josei(W)" to "josei-w",
    "Josei(W)" to "joseiw",
    "Kids" to "kids",
    "Leveling" to "leveling",
    "Loli" to "loli",
    "Lolicon" to "lolicon",
    "Long Strip" to "long-strip",
    "Mafia" to "mafia",
    "Magi" to "magi",
    "Magic" to "magic",
    "Magical Girls" to "magical-girls",
    "Manga" to "manga",
    "Manhua" to "manhua",
    "Manhwa" to "manhwa",
    "Martial Art" to "martial-art",
    "Martial Arts" to "martial-arts",
    "Mature" to "mature",
    "Mecha" to "mecha",
    "Medical" to "medical",
    "Military" to "military",
    "Mirror" to "mirror",
    "Modern" to "modern",
    "Monster Girls" to "monster-girls",
    "Monsters" to "monsters",
    "Murim" to "murim",
    "Music" to "music",
    "Mystery" to "mystery",
    "Necromancer" to "necromancer",
    "Ninja" to "ninja",
    "Non-human" to "non-human",
    "Office Workers" to "office-workers",
    "Official Colored" to "official-colored",
    "One-Shot" to "one-shot",
    "Oneshot" to "oneshot",
    "Overpowered" to "overpowered",
    "Parody" to "parody",
    "Pets" to "pets",
    "Philosophical" to "philosophical",
    "Police" to "police",
    "Post-Apocalyptic" to "post-apocalyptic",
    "Project" to "project",
    "Psychological" to "psychological",
    "Regression" to "regression",
    "Reincarnation" to "reincarnation",
    "Revenge" to "revenge",
    "Reverse Harem" to "reverse-harem",
    "Reverse Isekai" to "reverse-isekai",
    "Romance" to "romance",
    "Royal family" to "royal-family",
    "Royalty" to "royalty",
    "School Life" to "school-life",
    "School" to "school",
    "Sci-fi" to "sci-fi",
    "Seinen" to "seinen",
    "Seinen(M)" to "seinenm",
    "Seinin" to "seinin",
    "Sexual Violence" to "sexual-violence",
    "Shotacon" to "shotacon",
    "Shoujo Ai" to "shoujo-ai",
    "Shoujo" to "shoujo",
    "Shoujo(G)" to "shoujo-g",
    "Shoujo(G)" to "shoujog",
    "Shounen Ai" to "shounen-ai",
    "Shounen" to "shounen",
    "Shounen(B)" to "shounen-b",
    "Shounen(B)" to "shounenb",
    "Shounn" to "shounn",
    "Showbiz" to "showbiz",
    "Slice of Life" to "slice-of-life",
    "Smut" to "smut",
    "Space" to "space",
    "Sport" to "sport",
    "Sports" to "sports",
    "Super Power" to "super-power",
    "Superhero" to "superhero",
    "Supernatural" to "supernatural",
    "Supranatural" to "supranatural",
    "Survival" to "survival",
    "System" to "system",
    "Thriller" to "thriller",
    "Time Travel" to "time-travel",
    "Traditional Games" to "traditional-games",
    "Tragedy" to "tragedy",
    "Transmigration" to "transmigration",
    "Vampire" to "vampire",
    "Vampires" to "vampires",
    "Video Games" to "video-games",
    "Villainess" to "villainess",
    "Violence" to "violence",
    "Virtual Reality" to "virtual-reality",
    "Web Comic" to "web-comic",
    "Webtoon" to "webtoon",
    "Webtoons" to "webtoons",
    "Wuxia" to "wuxia",
    "Xianxia" to "xianxia",
    "Xuanhuan" to "xuanhuan",
    "Yaoi" to "yaoi",
    "Yuri" to "yuri",
    "Zombies" to "zombies",
)
