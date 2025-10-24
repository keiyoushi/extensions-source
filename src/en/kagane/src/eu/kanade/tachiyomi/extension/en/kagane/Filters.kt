package eu.kanade.tachiyomi.extension.en.kagane

import eu.kanade.tachiyomi.extension.en.kagane.Kagane.Companion.CONTENT_RATINGS
import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

internal class SortFilter(state: Int = 0) : UriPartFilter(
    "Sort By",
    arrayOf(
        Pair("Relevance", ""),
        Pair("Popular", "avg_views,desc"),
        Pair("Latest", "updated_at"),
        Pair("Latest Descending", "updated_at,desc"),
        Pair("By Name", "series_name"),
        Pair("By Name Descending", "series_name,desc"),
        Pair("Books count", "books_count"),
        Pair("Books count Descending", "books_count,desc"),
        Pair("Created at", "created_at"),
        Pair("Created at Descending", "created_at,desc"),
    ),
    state,
)

internal class ContentRatingFilter(
    defaultRatings: Set<String>,
    ratings: List<FilterData> = CONTENT_RATINGS.map { FilterData(it, it.replaceFirstChar { c -> c.uppercase() }) },
) : JsonMultiSelectFilter(
    "Content Rating",
    "content_rating",
    ratings.map {
        MultiSelectOption(it.name, it.id).apply {
            state = defaultRatings.contains(it.id)
        }
    },
)

internal class GenresFilter(
    genres: List<FilterData> = GENRES.map { FilterData(it, it) },
) : JsonMultiSelectTriFilter(
    "Genres",
    "genres",
    genres.map {
        MultiSelectTriOption(it.name, it.id)
    },
)

internal class TagsFilter(
    tags: List<FilterData> = TAGS.map { FilterData(it, it.replaceFirstChar { c -> c.uppercase() }) },
) : JsonMultiSelectTriFilter(
    "Tags",
    "tags",
    tags.map {
        MultiSelectTriOption(it.name, it.id)
    },
)

internal class SourcesFilter(
    sources: List<FilterData> = SOURCES.map { FilterData(it, it) },
) : JsonMultiSelectFilter(
    "Sources",
    "sources",
    sources.map {
        MultiSelectOption(it.name, it.id)
    },
)

internal class ScanlationsFilter() : Filter.CheckBox("Show scanlations", true)

internal class FilterData(
    val id: String,
    val name: String,
)

internal open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
    fun toUriPart() = vals[state].second
    val selected get() = vals[state].second.takeUnless { it.isEmpty() }
}

internal open class MultiSelectOption(name: String, val id: String = name) : Filter.CheckBox(name, false)

internal open class JsonMultiSelectFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectOption>,
) : Filter.Group<MultiSelectOption>(name, genres), JsonFilter {
    override fun addToJsonObject(builder: JsonObjectBuilder) {
        val whatToInclude = state.filter { it.state }.map { it.id }

        if (whatToInclude.isNotEmpty()) {
            builder.putJsonArray(param) {
                whatToInclude.forEach { add(it) }
            }
        }
    }
}

internal open class MultiSelectTriOption(name: String, val id: String = name) : Filter.TriState(name)

internal open class JsonMultiSelectTriFilter(
    name: String,
    private val param: String,
    genres: List<MultiSelectTriOption>,
) : Filter.Group<MultiSelectTriOption>(name, genres), JsonFilter {
    override fun addToJsonObject(builder: JsonObjectBuilder) {
        val whatToInclude = state.filter { it.state == TriState.STATE_INCLUDE }.map { it.id }
        val whatToExclude = state.filter { it.state == TriState.STATE_EXCLUDE }.map { it.id }

        with(builder) {
            if (whatToInclude.isNotEmpty()) {
                putJsonObject("inclusive_$param") {
                    putJsonArray("values") {
                        whatToInclude.forEach { add(it) }
                    }
                    put("match_all", true)
                }
            }
            if (whatToExclude.isNotEmpty()) {
                putJsonObject("exclusive_$param") {
                    putJsonArray("values") {
                        whatToExclude.forEach { add(it) }
                    }
                    put("match_all", false)
                }
            }
        }
    }
}

internal interface JsonFilter {
    fun addToJsonObject(builder: JsonObjectBuilder)
}

private val GENRES = listOf(
    "Romance",
    "Drama",
    "Manhwa",
    "Fantasy",
    "Comedy",
    "Manga",
    "Action",
    "LGBTQIA+",
    "Mature",
    "Shoujo",
    "Boys' Love",
    "Shounen",
    "Supernatural",
    "Josei",
    "Manhua",
    "Slice of Life",
    "Seinen",
    "Adventure",
    "School Life",
    "Yaoi",
    "Smut",
    "Historical",
    "Hentai",
    "Isekai",
    "Mystery",
    "Psychological",
    "Tragedy",
    "Harem",
    "Martial Arts",
    "Shounen Ai",
    "Science Fiction",
    "Horror",
    "Ecchi",
    "OEL",
    "Thriller",
    "Girls' Love",
    "Yuri",
    "Sports",
    "Coming of Age",
    "Gender Bender",
    "Suspense",
    "Music",
    "Shoujo Ai",
    "Award Winning",
    "Cooking",
    "Doujinshi",
    "Anime",
    "Mecha",
    "Magical Girls",
    "Philosophical",
    "Medical",
    "4-Koma",
    "Crime",
    "Animals",
    "Magic",
    "Oneshot",
    "Wuxia",
    "Anthology",
    "Superhero",
)

private val TAGS = listOf(
    "full color",
    "webtoons",
    "manhwa",
    "male protagonist",
    "webtoon",
    "heterosexual",
    "female protagonist",
    "based on a web novel",
    "long strip",
    "boys' love",
    "bl",
    "manhua",
    "nudity",
    "primarily adult cast",
    "magic",
    "reincarnation",
    "school",
    "isekai",
    "web comic",
    "revenge",
    "love triangles",
    "royalty",
    "love triangle",
    "person in a strange world",
    "european ambience",
    "nobility/aristocracy",
    "handsome male lead",
    "based on a novel",
    "nobility",
    "time skip",
    "age gap",
    "rape",
    "21st century",
    "time travel",
    "tragedy",
    "strong male lead",
    "historical",
    "demons",
    "adult couples",
    "fellatio",
    "work",
    "beautiful female lead",
    "shounen-ai",
    "mature romance",
    "childhood friends",
    "unrequited love",
    "primarily male cast",
    "bullying",
    "male lead falls in love first",
    "misunderstandings",
    "college",
    "female harem",
    "strong female lead",
    "black-haired male lead",
    "time manipulation",
    "university/post-secondary students",
    "masturbation",
    "martial arts",
    "high school students",
    "adapted to anime",
    "big breasts",
    "handjob",
    "anal sex",
    "cunnilingus",
    "urban fantasy",
    "seinen",
    "modern era",
    "super powers",
    "flashbacks",
    "lgbtq+ themes",
    "transported into a novel",
    "nakadashi",
    "dead family members",
    "past plays a big role",
    "shoujo",
    "second chance",
    "swordplay",
    "monsters",
    "shounen",
    "royal affairs",
    "non-human protagonists",
    "super power",
    "betrayal",
    "secret identity",
    "yandere",
    "age regression",
    "time rewind",
    "drama",
    "public sex",
    "special abilities",
    "gods",
    "game elements",
    "orphans",
    "cohabitation",
    "older uke younger seme",
    "cultivation",
    "tl",
    "adult cast",
    "murders",
    "fellatio/blowjob",
    "transmigration",
    "violence",
    "urban",
    "foreign",
    "high school",
    "korea",
    "fantasy world",
    "friendship",
    "traumatic past",
    "politics",
    "amnesia",
    "gore",
    "suicides",
    "threesome",
    "dead parents",
    "smart female lead",
    "anti-hero",
    "sex toys",
    "villainess",
    "blackmail",
    "princes",
    "arranged marriage",
    "defloration",
    "crossdressing",
    "marriage",
    "south korea",
    "danmei",
    "netorare",
    "fantasy",
    "oel",
    "dragons",
    "tsundere",
    "romance",
    "reunions",
    "weak to strong",
    "interspecies relationship",
    "survival",
    "sci fi",
    "smart male lead",
    "primarily teen cast",
    "dubious consent",
    "japan",
    "coming of age",
    "character growth",
    "slow romance",
    "student-student relationship",
    "ceos",
    "crimes",
    "older female younger male",
    "family life",
    "jealousy",
    "video games",
    "rich male lead",
    "virginity",
    "tragic past",
    "older male younger female",
    "wars",
    "based on a light novel",
    "first-time intercourse",
    "yuri",
    "twins",
    "beautiful artwork",
    "office workers",
    "assassins",
    "arrogant characters",
    "kind male lead",
    "dukes",
    "crime",
    "acting",
    "teachers",
    "bisexual",
    "suicide",
    "deepthroat",
    "first love",
    "adult couple",
    "josei",
    "bondage",
    "university/college",
    "curses",
    "axed/cancelled/discontinued",
    "borderline h",
    "ghosts",
    "office lady",
    "animals",
    "kidnappings",
    "mages",
    "maids",
    "blood and gore",
    "cheating/infidelity",
    "knights",
    "long-haired male lead",
    "attempted murder",
    "multiple couples",
    "coworkers",
    "primarily female cast",
    "comedy",
    "trauma",
    "attempted rape",
    "human-nonhuman relationship",
    "idols",
)

private val SOURCES = listOf(
    "Asura Scans",
    "Comikey",
    "Dark Horse Comics",
    "Day Comics",
    "FAKKU",
    "Flame Comics",
    "Grim Scans",
    "Hive Toons",
    "INKR Comics",
    "J-Novel Club",
    "Kana",
    "Kenscans",
    "Kodansha Comics",
    "Lezhin",
    "Luna Toons",
    "Madarascans",
    "MangaDex",
    "Manta",
    "Nyx Scans",
    "One Peace Books",
    "Others",
    "Pocket Comics",
    "Raven Scans",
    "Reset Scans",
    "Rizz Fables",
    "Rokari Comics",
    "Seven Seas Entertainment",
    "Siren Scans",
    "Square Enix Manga",
    "StoneScape",
    "TOKYOPOP",
    "Tapas",
    "Tappytoon",
    "Temple Scan",
    "Thunderscans",
    "Toomics",
    "UDON Entertainment",
    "VAST Visual",
    "VIZ Media",
    "Vortex Scans",
    "Webcomics",
    "Webtoon",
    "Yen Press",
)
