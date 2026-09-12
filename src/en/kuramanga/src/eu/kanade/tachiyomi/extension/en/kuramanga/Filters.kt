package eu.kanade.tachiyomi.extension.en.kuramanga

import eu.kanade.tachiyomi.source.model.Filter

abstract class SelectFilter(name: String, val vals: Array<String>) : Filter.Select<String>(name, vals)

class StatusFilter(name: String, vals: Array<String>) : SelectFilter(name, vals)

class GenreFilter(name: String, genres: List<Genre>) : Filter.Group<Genre>(name, genres)

class Genre(name: String) : Filter.CheckBox(name)

class AdultFilter(name: String) : Filter.CheckBox(name, true)

internal val statusList = arrayOf("All", "Ongoing", "Completed", "Hiatus", "On Hold", "Canceled")

internal val genreNames = listOf(
    "+100 Chapter", "Ability", "Academy", "Acting", "Action", "Adaptation", "Adult", "Adventure", "Ai", "Aliens",
    "Animals", "Anthology", "Apocalypse", "Award Winning", "Battleofintellect", "BDSM", "BL", "Borderline H",
    "Boys Love", "Bullying", "Campus", "Cheating/infidelity", "Cohabitation", "College", "College life", "Comedy",
    "Comic", "Cooking", "Crazy MC", "Crime", "Crossdressing", "Cultivation", "Curse", "Dark Fantasy", "Darkfantasy",
    "Delinquents", "Demon", "Demons", "Difference in Status", "Doujinshi", "Drama", "Dungeons", "Ecchi", "Elementals",
    "Elf", "Explicit Sex", "Family", "Fantasia", "Fantasy", "Fight", "Folklore", "Friday Webtoons", "Full Color",
    "Game", "Gamelit", "Gang", "Gender Bender", "Genderswap", "Genius", "Genius MC", "Ghosts", "Girls Love", "GL",
    "Gore", "Growth", "Guideverse", "Hardcore", "Harem", "Hentai", "Hidden", "Historical", "Horror", "Humiliation",
    "Hunter", "Hunters", "Hypnosis", "Idols", "Illusion", "Incest", "Isekai", "Josei", "Legendary", "Live",
    "Long Strip", "Love Triangle", "Mafia", "Magic", "Magical", "Manhua", "Manhwa", "Married Woman", "Martial Arts",
    "Mature", "Mecha", "Medical", "Milf", "Military", "Modernfantasy", "Money", "Monster Girls", "Monsters", "Mother",
    "Mother and Daughter", "Murim", "Music", "Mystery", "Myth", "Necromancer", "Netori", "Nonhumanbeings",
    "Novel Adaptation", "Ntl", "NTR", "Office", "Office Workers", "Omegaverse", "One shot", "Original Novel",
    "Overpowered", "Overpoweredmc", "Philosophical", "Politics", "Post-Apocalyptic", "Psychological", "Raw", "Reborn",
    "Regression", "Reincarnation", "Returner", "Revenge", "Reverse Harem", "Robots", "Romance", "Royal family",
    "School", "School Life", "Schoollife", "Sci-Fi", "Seinen", "Serial", "Short Story", "Shoujo", "Shounen",
    "Shounen Ai", "Showbiz", "Sisters", "Slice of Life", "Sm", "Smut", "Sport", "Sports", "Stepmother", "Superhero",
    "Supernatural", "Superpower", "Survival", "Swapping", "Swordsman", "System", "Teacher", "Threesome", "Thriller",
    "Time Travel", "Tower", "Traditional Games", "Tragedy", "Training", "Transmigration", "Uncensored", "Urban",
    "Vampires", "Video Games", "Villain", "Villainess", "Violence", "Virtual Reality", "Visualshock", "Web Comic",
    "Webtoon", "Webtoons", "Wholesome", "Workplace", "Wuxia", "Xuanhuan", "Yaoi", "Yuri", "Zombies",
)
