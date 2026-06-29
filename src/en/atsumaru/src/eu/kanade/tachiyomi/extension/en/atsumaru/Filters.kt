package eu.kanade.tachiyomi.extension.en.atsumaru

import eu.kanade.tachiyomi.source.model.Filter

internal class GenreFilter(genres: List<Genre>) :
    Filter.Group<Filter.TriState>(
        "Genres",
        genres.map { genre ->
            object : Filter.TriState(genre.name) {}
        },
    ) {
    val genreIds = genres.map { it.id }
}

internal class TagsFilter(tags: List<Tag>) :
    Filter.Group<Filter.TriState>(
        "Tags",
        tags.map { tag ->
            object : Filter.TriState(tag.name) {}
        },
    ) {
    val tagIds = tags.map { it.id }
}

internal class TypeFilter(types: List<Type>) :
    Filter.Group<Filter.CheckBox>(
        "Manga Type",
        types.map { type ->
            object : Filter.CheckBox(type.name, false) {}
        },
    ) {
    val ids = types.map { it.id }
}

internal class StatusFilter(statuses: List<Status>) :
    Filter.Group<Filter.CheckBox>(
        "Publishing Status",
        statuses.map { status ->
            object : Filter.CheckBox(status.name, false) {}
        },
    ) {
    val ids = statuses.map { it.id }
}

internal class YearFilter : Filter.Text("Year (e.g., 2024)")

internal class MinChaptersFilter : Filter.Text("Minimum Chapters")

internal class SortFilter :
    Filter.Sort(
        "Sort By",
        arrayOf("Popularity", "Trending", "Date Added", "Release Date", "Top Rated", "Title"),
        Selection(0, false),
    ) {
    companion object {
        val VALUES = arrayOf("views", "trending", "dateAdded", "released", "avgRating", "title")
    }
}

internal class AdultFilter(state: Boolean) : Filter.CheckBox("Show Adult Content", state)

internal class OfficialFilter : Filter.CheckBox("Only Official Translations", false)

internal data class Genre(val name: String, val id: String)

internal data class Tag(val name: String, val id: String)

internal data class Type(val name: String, val id: String)

internal data class Status(val name: String, val id: String)

internal fun getGenresList() = listOf(
    Genre("Action", "39"),
    Genre("Adult", "46"),
    Genre("Adventure", "37"),
    Genre("Boys Love", "180"),
    Genre("Comedy", "6"),
    Genre("Drama", "31"),
    Genre("Fantasy", "36"),
    Genre("Girls Love", "4"),
    Genre("Hentai", "10"),
    Genre("Historical", "45"),
    Genre("Horror", "44"),
    Genre("Martial Arts", "29"),
    Genre("Mystery", "32"),
    Genre("Psychological", "18"),
    Genre("Romance", "9"),
    Genre("Sci-Fi", "1"),
    Genre("Slice of Life", "7"),
    Genre("Smut", "41"),
    Genre("Supernatural", "22"),
    Genre("Thriller", "19"),
    Genre("Tragedy", "5"),
)

internal fun getTagsList() = listOf(
    Tag("Blackmail", "285"),
    Tag("Cooking", "669"),
    Tag("Crimes", "288"),
    Tag("Crossdressing", "167"),
    Tag("Murder", "250"),
    Tag("Prostitution", "366"),
    Tag("Swordplay", "337"),
    Tag("Working", "248"),
    Tag("Josei", "43"),
    Tag("Seinen", "8"),
    Tag("Shoujo", "40"),
    Tag("Shounen", "38"),
    Tag("Otaku", "264"),
    Tag("Tsundere", "313"),
    Tag("Yandere", "315"),
    Tag("Animal Characteristics", "274"),
    Tag("Beautiful Female Lead", "72"),
    Tag("Big Breasts", "123"),
    Tag("Flat Chest", "320"),
    Tag("Glasses-Wearing Male Lead", "71"),
    Tag("Handsome Male Lead", "68"),
    Tag("Kemonomimi", "279"),
    Tag("MILF", "339"),
    Tag("Small Breasts", "124"),
    Tag("Young Male Lead", "787"),
    Tag("Adult Cast", "159"),
    Tag("Bisexual", "382"),
    Tag("Ensemble Cast", "362"),
    Tag("Female Lead", "59"),
    Tag("Male Lead", "58"),
    Tag("Non-Human Protagonist", "247"),
    Tag("Primarily Adult Cast", "158"),
    Tag("Primarily Female Cast", "333"),
    Tag("Primarily Male Cast", "335"),
    Tag("Primarily Teen Cast", "334"),
    Tag("Strong Female Lead", "69"),
    Tag("Strong Male Lead", "67"),
    Tag("Adapted to Anime", "166"),
    Tag("Based on a Light Novel", "76"),
    Tag("Based on a Novel", "75"),
    Tag("Based on a Video Game", "77"),
    Tag("Based on a Web Novel", "74"),
    Tag("College", "257"),
    Tag("Company", "1205"),
    Tag("Countryside", "415"),
    Tag("Europe", "405"),
    Tag("Foreign", "336"),
    Tag("High School", "162"),
    Tag("Hospital", "760"),
    Tag("Japan", "225"),
    Tag("School", "107"),
    Tag("School Clubs", "356"),
    Tag("Amnesia", "283"),
    Tag("Appearance Different from Personality", "651"),
    Tag("Caught in the Act", "874"),
    Tag("Dead Family Member", "831"),
    Tag("Family Drama", "848"),
    Tag("Flashbacks", "449"),
    Tag("Gender Bender", "12"),
    Tag("Love Triangle", "125"),
    Tag("Male Lead Falls in Love First", "653"),
    Tag("Misunderstandings", "647"),
    Tag("Past Plays a Big Role", "648"),
    Tag("Reincarnation", "126"),
    Tag("Secret Identity", "260"),
    Tag("Time Manipulation", "311"),
    Tag("Time Skip", "172"),
    Tag("Time Travel", "249"),
    Tag("Tragic Past", "898"),
    Tag("Weak to Strong", "1064"),
    Tag("Delinquents", "239"),
    Tag("Detectives", "240"),
    Tag("Idols", "281"),
    Tag("Maids", "116"),
    Tag("Office Lady", "312"),
    Tag("Office Worker", "429"),
    Tag("School Girl", "788"),
    Tag("Teachers", "175"),
    Tag("Age Gap", "106"),
    Tag("Childhood Friends", "97"),
    Tag("Coworkers", "286"),
    Tag("Female Harem", "163"),
    Tag("Friends to Lovers", "243"),
    Tag("Friendship", "242"),
    Tag("Harem", "20"),
    Tag("Heterosexual", "108"),
    Tag("Incest", "174"),
    Tag("Infidelity", "231"),
    Tag("Interspecies Relationship", "308"),
    Tag("Love-Hate Relationship", "889"),
    Tag("Master-Servant Relationship", "406"),
    Tag("Older Female Younger Male", "114"),
    Tag("Older Male Younger Female", "649"),
    Tag("Older Uke Younger Seme", "880"),
    Tag("Siblings", "254"),
    Tag("Student-Student Relationship", "573"),
    Tag("Student-Teacher Relationship", "177"),
    Tag("Twins", "253"),
    Tag("Chinese Ambience", "588"),
    Tag("European Ambience", "450"),
    Tag("Fantasy World", "642"),
    Tag("Feudal Japan", "606"),
    Tag("Game Elements", "399"),
    Tag("Game World", "641"),
    Tag("Isekai", "94"),
    Tag("Isekaied Into a Novel", "258"),
    Tag("Mecha", "11"),
    Tag("Mythology", "259"),
    Tag("Urban", "338"),
    Tag("Urban Fantasy", "261"),
    Tag("Anal Intercourse", "100"),
    Tag("Bondage", "280"),
    Tag("Boobjob", "381"),
    Tag("Borderline H", "448"),
    Tag("Cunnilingus", "171"),
    Tag("Defloration", "306"),
    Tag("Dubious Consent", "985"),
    Tag("Ecchi", "21"),
    Tag("Erotica", "14"),
    Tag("Exhibitionism", "287"),
    Tag("Group Intercourse", "373"),
    Tag("Handjob", "303"),
    Tag("Lolicon", "28"),
    Tag("Masturbation", "161"),
    Tag("Mature", "15"),
    Tag("Nakadashi", "169"),
    Tag("Netorare", "232"),
    Tag("Nudity", "109"),
    Tag("Oral Intercourse", "99"),
    Tag("Outdoor Intercourse", "307"),
    Tag("Public Intercourse", "103"),
    Tag("Rape", "95"),
    Tag("Sex Addict", "650"),
    Tag("Sex Toys", "289"),
    Tag("Shotacon", "35"),
    Tag("Teens Love", "374"),
    Tag("Threesome", "173"),
    Tag("Virginity", "369"),
    Tag("Animals", "278"),
    Tag("Cats", "284"),
    Tag("Demons", "160"),
    Tag("Ghosts", "229"),
    Tag("Gods", "176"),
    Tag("Monsters", "395"),
    Tag("Non-human", "547"),
    Tag("Vampires", "252"),
    Tag("21st century", "132"),
    Tag("Betrayal", "403"),
    Tag("Bullying", "235"),
    Tag("Cohabitation", "228"),
    Tag("Coming of Age", "117"),
    Tag("Danmei", "305"),
    Tag("Depression", "1090"),
    Tag("Family Life", "282"),
    Tag("Female Empowerment", "1816"),
    Tag("Forbidden Love", "699"),
    Tag("Gore", "262"),
    Tag("Gourmet", "2"),
    Tag("Harlequin", "304"),
    Tag("Jealousy", "881"),
    Tag("LGBTQ+", "326"),
    Tag("Love Confession", "882"),
    Tag("Marriage", "360"),
    Tag("Mature Romance", "241"),
    Tag("Medical", "350"),
    Tag("Military", "230"),
    Tag("Music", "27"),
    Tag("Nobility", "127"),
    Tag("Obsessive Love", "893"),
    Tag("Orphans", "237"),
    Tag("Religion", "498"),
    Tag("Reunion", "984"),
    Tag("Revenge", "227"),
    Tag("Royalty", "128"),
    Tag("School Life", "42"),
    Tag("Shoujo Ai", "47"),
    Tag("Shounen Ai", "23"),
    Tag("Special Ability", "883"),
    Tag("Sports", "30"),
    Tag("Suicide", "309"),
    Tag("Super Powers", "236"),
    Tag("Unrequited Love", "226"),
    Tag("Violence", "830"),
    Tag("War", "238"),
    Tag("Yaoi", "16"),
    Tag("Yuri", "33"),
    Tag("4-Koma", "105"),
    Tag("Anthology", "113"),
    Tag("Chinese Novels", "1112"),
    Tag("Collection of Stories", "111"),
    Tag("Doujinshi", "24"),
    Tag("Episodic", "115"),
    Tag("Full color", "57"),
    Tag("Korean Novels", "1111"),
    Tag("Light Novel", "466"),
    Tag("Longstrip", "93"),
    Tag("One Shot", "110"),
    Tag("Web Comic", "428"),
    Tag("Web Novel", "427"),
    Tag("Magic", "121"),
)

internal fun getTypesList() = listOf(
    Type("Manga", "Manga"),
    Type("Manhwa", "Manwha"),
    Type("Manhua", "Manhua"),
    Type("OEL", "OEL"),
)

internal fun getStatusList() = listOf(
    Status("Ongoing", "Ongoing"),
    Status("Completed", "Completed"),
    Status("Hiatus", "Hiatus"),
    Status("Canceled", "Canceled"),
)
