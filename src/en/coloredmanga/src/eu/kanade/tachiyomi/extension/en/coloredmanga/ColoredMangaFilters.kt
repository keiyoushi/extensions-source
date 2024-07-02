@file:JvmName("ColoredMangaKt")

package eu.kanade.tachiyomi.extension.en.coloredmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList {
    return FilterList(
        SortFilter("Sort by", Filter.Sort.Selection(0, false), getSortsList),
        TypeFilter("Types"),
        ColorFilter("Color"),
        StatusFilter("Status"),
        GenreFilter("Genre"),
    )
}

internal class ColorFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "B/W",
            "Color",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class TypeFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "Manga",
            "Manwha",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class StatusFilter(name: String) :
    Filter.Group<TriFilter>(
        name,
        listOf(
            "Ongoing",
            "Completed",
            "Cancelled",
            "Hiatus",
        ).map { TriFilter(it, it.lowercase()) },
    )

internal class GenreFilter(name: String) : TextFilter(name)

internal open class TriFilter(name: String, val value: String) : Filter.TriState(name)

internal open class TextFilter(name: String) : Filter.Text(name)

internal open class SelectFilter(name: String, private val vals: List<Pair<String, String>>, state: Int = 0) :
    Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state) {
    fun getValue() = vals[state].second
}

internal open class SortFilter(name: String, selection: Selection, private val vals: List<Pair<String, String>>) :
    Filter.Sort(name, vals.map { it.first }.toTypedArray(), selection) {
    fun getValue() = vals[state!!.index].second
}

private val getGenresList: List<String> = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo",
    "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
    "Sports", "Supernatural", "Thriller", "4-koma", "Achromatic", "Achronological Order",
    "Acrobatics", "Acting", "Adoption", "Advertisement", "Afterlife", "Age Gap",
    "Age Regression", "Agender", "Agriculture", "Airsoft", "Alchemy", "Aliens",
    "Alternate Universe", "American Football", "Amnesia", "Anachronism", "Ancient China",
    "Angels", "Animals", "Anthology", "Anthropomorphism", "Anti-Hero", "Archery",
    "Arranged Marriage", "Artificial Intelligence", "Asexual", "Assassins", "Astronomy",
    "Athletics", "Augmented Reality", "Autobiographical", "Aviation", "Badminton",
    "Band", "Bar", "Baseball", "Basketball", "Battle Royale", "Biographical",
    "Bisexual", "Board Game", "Boarding School", "Body Horror", "Body Swapping",
    "Boxing", "Boys' Love", "Bullying", "Butler", "Calligraphy", "Cannibalism",
    "Card Battle", "Cars", "Centaur", "CGI", "Cheerleading", "Chibi",
    "Chimera", "Chuunibyou", "Circus", "Class Struggle", "Classic Literature",
    "Clone", "Coastal", "College", "Coming of Age", "Conspiracy", "Cosmic Horror",
    "Cosplay", "Crime", "Criminal Organization", "Crossdressing", "Crossover", "Cult",
    "Cultivation", "Cute Boys Doing Cute Things", "Cute Girls Doing Cute Things",
    "Cyberpunk", "Cyborg", "Cycling", "Dancing", "Death Game", "Delinquents",
    "Demons", "Denpa", "Desert", "Detective", "Dinosaurs", "Disability",
    "Dissociative Identities", "Dragons", "Drawing", "Drugs", "Dullahan",
    "Dungeon", "Dystopian", "E-Sports", "Economics", "Educational", "Elf",
    "Ensemble Cast", "Environmental", "Episodic", "Ero Guro", "Espionage",
    "Estranged Family", "Fairy", "Fairy Tale", "Family Life", "Fashion",
    "Female Harem", "Female Protagonist", "Femboy", "Fencing", "Filmmaking",
    "Firefighters", "Fishing", "Fitness", "Flash", "Food", "Football",
    "Foreign", "Found Family", "Fugitive", "Full CGI", "Full Color",
    "Gambling", "Gangs", "Gender Bending", "Ghost", "Go", "Goblin",
    "Gods", "Golf", "Gore", "Guns", "Gyaru", "Handball",
    "Henshin", "Heterosexual", "Hikikomori", "Historical", "Homeless", "Horticulture",
    "Ice Skating", "Idol", "Inn", "Isekai", "Iyashikei", "Josei",
    "Judo", "Kaiju", "Karuta", "Kemonomimi", "Kids", "Kingdom Management",
    "Konbini", "Kuudere", "Lacrosse", "Language Barrier", "LGBTQ+ Themes", "Lost Civilization",
    "Love Triangle", "Mafia", "Magic", "Mahjong", "Maids", "Makeup",
    "Male Harem", "Male Protagonist", "Marriage", "Martial Arts", "Matriarchy",
    "Medicine", "Memory Manipulation", "Mermaid", "Meta", "Military", "Mixed Gender Harem",
    "Monster Boy", "Monster Girl", "Mopeds", "Motorcycles", "Mountaineering", "Musical",
    "Mythology", "Natural Disaster", "Necromancy", "Nekomimi", "Ninja", "No Dialogue",
    "Noir", "Non-fiction", "Nudity", "Nun", "Office", "Office Lady",
    "Oiran", "Ojou-sama", "Orphan", "Otaku Culture", "Outdoor", "Pandemic",
    "Parkour", "Parody", "Philosophy", "Photography", "Pirates", "Poker",
    "Police", "Politics", "Polyamorous", "Post-Apocalyptic", "POV", "Primarily Adult Cast",
    "Primarily Animal Cast", "Primarily Child Cast", "Primarily Female Cast", "Primarily Male Cast",
    "Primarily Teen Cast", "Prison", "Proxy Battle", "Puppetry", "Rakugo", "Real Robot",
    "Rehabilitation", "Reincarnation", "Religion", "Revenge", "Robots", "Rotoscoping",
    "Royal Affairs", "Rugby", "Rural", "Samurai", "Satire", "School",
    "School Club", "Scuba Diving", "Seinen", "Shapeshifting", "Ships", "Shogi",
    "Shoujo", "Shounen", "Shrine Maiden", "Skateboarding", "Skeleton", "Slapstick",
    "Slavery", "Snowscape", "Software Development", "Space", "Space Opera", "Spearplay",
    "Steampunk", "Stop Motion", "Succubus", "Suicide", "Sumo", "Super Power",
    "Super Robot", "Superhero", "Surfing", "Surreal Comedy", "Survival", "Swimming",
    "Swordplay", "Table Tennis", "Tanks", "Tanned Skin", "Teacher", "Teens' Love",
    "Tennis", "Terrorism", "Time Loop", "Time Manipulation", "Time Skip", "Tokusatsu",
    "Tomboy", "Torture", "Tragedy", "Trains", "Transgender", "Travel",
    "Triads", "Tsundere", "Twins", "Unrequited Love", "Urban", "Urban Fantasy",
    "Vampire", "Veterinarian", "Video Games", "Vikings", "Villainess", "Virtual World",
    "Volleyball", "VTuber", "War", "Werewolf", "Witch", "Work",
    "Wrestling", "Writing", "Wuxia", "Yakuza", "Yandere", "Youkai",
    "Yuri", "Zombie", "Artbook", "chromatique",
)

private val getSortsList: List<Pair<String, String>> = listOf(
    Pair("Newest", "lat"),
    Pair("Popularity", "pop"),
    Pair("Title", "tit"),
)
