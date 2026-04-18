package eu.kanade.tachiyomi.extension.en.manhwazone

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Sort By",
        arrayOf(
            Pair("Popularity", "popularity"),
            Pair("Latest", "latest"),
            Pair("Rank", "rank"),
            Pair("Score", "score"),
            Pair("Follower", "follower"),
            Pair("A → Z", "name_asc"),
            Pair("Z → A", "name_desc"),
        ),
    )

class StatusFilter :
    UriPartFilter(
        "Status",
        arrayOf(
            Pair("All Status", ""),
            Pair("Finished", "finished"),
            Pair("On Hiatus", "on_hiatus"),
            Pair("On Going", "currently_publishing"),
            Pair("Discontinued", "discontinued"),
        ),
    )

class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

class GenreFilterGroup(genres: List<GenreCheckBox>) : Filter.Group<GenreCheckBox>("Genres", genres)

fun getGenreList() = listOf(
    GenreCheckBox("Action", "action"),
    GenreCheckBox("Adventure", "adventure"),
    GenreCheckBox("Avant Garde", "avant-garde"),
    GenreCheckBox("Award Winning", "award-winning"),
    GenreCheckBox("Boys Love", "boys-love"),
    GenreCheckBox("Comedy", "comedy"),
    GenreCheckBox("Drama", "drama"),
    GenreCheckBox("Fantasy", "fantasy"),
    GenreCheckBox("Girls Love", "girls-love"),
    GenreCheckBox("Gourmet", "gourmet"),
    GenreCheckBox("Horror", "horror"),
    GenreCheckBox("Mystery", "mystery"),
    GenreCheckBox("Romance", "romance"),
    GenreCheckBox("Sci-Fi", "sci-fi"),
    GenreCheckBox("Slice of Life", "slice-of-life"),
    GenreCheckBox("Sports", "sports"),
    GenreCheckBox("Supernatural", "supernatural"),
    GenreCheckBox("Suspense", "suspense"),
    GenreCheckBox("Urban Fantasy", "urban-fantasy"),
    GenreCheckBox("Ecchi", "ecchi"),
    GenreCheckBox("Erotica", "erotica"),
    GenreCheckBox("Hentai", "hentai"),
    GenreCheckBox("Adult Cast", "adult-cast"),
    GenreCheckBox("Anthropomorphic", "anthropomorphic"),
    GenreCheckBox("CGDCT", "cgdct"),
    GenreCheckBox("Childcare", "childcare"),
    GenreCheckBox("Combat Sports", "combat-sports"),
    GenreCheckBox("Crossdressing", "crossdressing"),
    GenreCheckBox("Delinquents", "delinquents"),
    GenreCheckBox("Detective", "detective"),
    GenreCheckBox("Educational", "educational"),
    GenreCheckBox("Gag Humor", "gag-humor"),
    GenreCheckBox("Gore", "gore"),
    GenreCheckBox("Harem", "harem"),
    GenreCheckBox("High Stakes Game", "high-stakes-game"),
    GenreCheckBox("Historical", "historical"),
    GenreCheckBox("Idols (Female)", "idols-female"),
    GenreCheckBox("Idols (Male)", "idols-male"),
    GenreCheckBox("Isekai", "isekai"),
    GenreCheckBox("Iyashikei", "iyashikei"),
    GenreCheckBox("Love Polygon", "love-polygon"),
    GenreCheckBox("Magical Sex Shift", "magical-sex-shift"),
    GenreCheckBox("Mahou Shoujo", "mahou-shoujo"),
    GenreCheckBox("Martial Arts", "martial-arts"),
    GenreCheckBox("Mecha", "mecha"),
    GenreCheckBox("Medical", "medical"),
    GenreCheckBox("Memoir", "memoir"),
    GenreCheckBox("Military", "military"),
    GenreCheckBox("Music", "music"),
    GenreCheckBox("Mythology", "mythology"),
    GenreCheckBox("Organized Crime", "organized-crime"),
    GenreCheckBox("Otaku Culture", "otaku-culture"),
    GenreCheckBox("Parody", "parody"),
    GenreCheckBox("Performing Arts", "performing-arts"),
    GenreCheckBox("Pets", "pets"),
    GenreCheckBox("Psychological", "psychological"),
    GenreCheckBox("Racing", "racing"),
    GenreCheckBox("Reincarnation", "reincarnation"),
    GenreCheckBox("Reverse Harem", "reverse-harem"),
    GenreCheckBox("Romantic Subtext", "romantic-subtext"),
    GenreCheckBox("Samurai", "samurai"),
    GenreCheckBox("School", "school"),
    GenreCheckBox("Showbiz", "showbiz"),
    GenreCheckBox("Space", "space"),
    GenreCheckBox("Strategy Game", "strategy-game"),
    GenreCheckBox("Super Power", "super-power"),
    GenreCheckBox("Survival", "survival"),
    GenreCheckBox("Team Sports", "team-sports"),
    GenreCheckBox("Time Travel", "time-travel"),
    GenreCheckBox("Vampire", "vampire"),
    GenreCheckBox("Video Game", "video-game"),
    GenreCheckBox("Villainess", "villainess"),
    GenreCheckBox("Visual Arts", "visual-arts"),
    GenreCheckBox("Workplace", "workplace"),
    GenreCheckBox("Josei", "josei"),
    GenreCheckBox("Kids", "kids"),
    GenreCheckBox("Seinen", "seinen"),
    GenreCheckBox("Shoujo", "shoujo"),
    GenreCheckBox("Shounen", "shounen"),
)
