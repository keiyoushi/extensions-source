package eu.kanade.tachiyomi.extension.en.theblank

import eu.kanade.tachiyomi.multisrc.pam.CheckBoxGroup
import eu.kanade.tachiyomi.multisrc.pam.NumberFilter
import eu.kanade.tachiyomi.multisrc.pam.Pam
import eu.kanade.tachiyomi.multisrc.pam.SortFilter
import eu.kanade.tachiyomi.multisrc.pam.TriStateGroupFilter
import eu.kanade.tachiyomi.multisrc.pam.TypeGroupFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement

@Source
abstract class TheBlank : Pam() {

    override val popularFilters = FilterList(SortFilter("Sort", sortValues, Filter.Sort.Selection(3, false)))
    override val latestFilters = FilterList(SortFilter("Sort", sortValues, Filter.Sort.Selection(2, false)))

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter("Sort", sortValues),
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        NumberFilter("Year from", "year_min"),
        NumberFilter("Year to", "year_max"),
        NumberFilter("Chapters from", "chapters_min"),
        NumberFilter("Chapters to", "chapters_max"),
    )

    private class GenreFilter : TriStateGroupFilter("Genres", genres)
    private class TypeFilter : TypeGroupFilter("Types", type)
    private class StatusFilter : CheckBoxGroup("Status", status)
}

private val sortValues = listOf(
    "New Series" to "date",
    "Trending" to "trending",
    "Recently Updated" to "recently",
    "Most Views" to "views",
    "A-Z" to "alphabetical",
    "Chapters" to "chapters",
)

private val genres = listOf(
    "Action" to "action",
    "Adaptation" to "adaptation",
    "Adventure" to "adventure",
    "Agegap" to "agegap",
    "Ahjussi" to "ahjussi",
    "Ai" to "ai",
    "Ajumma" to "ajumma",
    "Alien" to "alien",
    "Alpha" to "alpha",
    "Anal" to "anal",
    "Animated" to "animated",
    "Anthology" to "anthology",
    "Aunt" to "aunt",
    "Bald" to "bald",
    "Bdsm" to "bdsm",
    "Big breasts" to "big-breasts",
    "Big dick" to "big-dick",
    "Bisexual" to "bisexual",
    "Blackmail" to "blackmail",
    "Blowjob" to "blowjob",
    "Bondage" to "bondage",
    "Boys love" to "boys-love",
    "Brother" to "brother",
    "Bukkake" to "bukkake",
    "Cheating/infidelity" to "cheatinginfidelity",
    "Childhood friend" to "childhood-friend",
    "Cohabitation" to "cohabitation",
    "College" to "college",
    "Comedy" to "comedy",
    "Corruption" to "corruption",
    "Cousin" to "cousin",
    "Crime" to "crime",
    "Cuckhold" to "cuckhold",
    "Cultivation" to "cultivation",
    "Cunnilingus" to "cunnilingus",
    "Different world" to "different-world",
    "Doujinshi" to "doujinshi",
    "Drama" to "drama",
    "Elf" to "elf",
    "Fantasy" to "fantasy",
    "First time" to "first-time",
    "Folklore" to "folklore",
    "Friends to lovers" to "friends-to-lovers",
    "Gender bender" to "gender-bender",
    "Guideverse" to "guideverse",
    "Hardcore" to "hardcore",
    "Harem" to "harem",
    "Historical" to "historical",
    "Horror" to "horror",
    "Humiliation" to "humiliation",
    "Hypnosis" to "hypnosis",
    "Isekai" to "isekai",
    "Josei" to "josei",
    "Love triangle" to "love-triangle",
    "Married woman" to "married-woman",
    "Martial arts" to "martial-arts",
    "Mature" to "mature",
    "Milf" to "milf",
    "Monsters" to "monsters",
    "Mother" to "mother",
    "Mother and daughter" to "mother-and-daughter",
    "Murim" to "murim",
    "Mystery" to "mystery",
    "Noir" to "noir",
    "Novel" to "novel",
    "Ntr" to "ntr",
    "Office workers" to "office-workers",
    "Omegaverse" to "omegaverse",
    "Parody" to "parody",
    "Psychological" to "psychological",
    "Raw" to "raw",
    "Reincarnation" to "reincarnation",
    "Revenge" to "revenge",
    "Robots" to "robots",
    "Romance" to "romance",
    "School life" to "school-life",
    "Sci-fi" to "sci-fi",
    "Seinen" to "seinen",
    "Sex friend" to "sex-friend",
    "Short story" to "short-story",
    "Shoujo" to "shoujo",
    "Shounen" to "shounen",
    "Showbiz" to "showbiz",
    "Sisters" to "sisters",
    "Slice of life" to "slice-of-life",
    "Smut" to "smut",
    "Sports" to "sports",
    "Stepmother" to "stepmother",
    "Supernatural" to "supernatural",
    "Superpower" to "superpower",
    "Survival" to "survival",
    "Suspense" to "suspense",
    "Swapping" to "swapping",
    "System" to "system",
    "Teacher" to "teacher",
    "Threesome" to "threesome",
    "Thriller" to "thriller",
    "Time travel" to "time-travel",
    "Uncensored" to "uncensored",
    "Vanilla" to "vanilla",
    "Violence" to "violence",
    "Wholesome" to "wholesome",
    "Wife" to "wife",
    "Workplace" to "workplace",
)

private val type = listOf(
    "Boys love" to "boys-love",
    "Comic" to "comic",
    "Doujin" to "doujin",
    "Josei" to "josei",
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Pornhwa" to "pornhwa",
    "Webtoon" to "webtoon",
)

private val status = listOf(
    "Ongoing" to "ongoing",
    "Finished" to "finished",
    "Dropped" to "dropped",
    "On Hold" to "onhold",
    "Upcoming" to "upcoming",
)
