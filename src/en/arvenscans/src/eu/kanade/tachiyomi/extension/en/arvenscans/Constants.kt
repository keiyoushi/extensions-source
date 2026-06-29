package eu.kanade.tachiyomi.extension.en.arvenscans

import java.util.Locale

const val PER_PAGE = 18

const val SERIES_PATH_SEGMENT = "series"

const val API_QUERY_PATH = "/api/query"
const val API_POST_PATH = "/api/post"
const val API_CHAPTER_PATH = "/api/chapter"

const val STATUS_FILTER_KEY = "seriesStatus"
val STATUS_OPTIONS: Options = listOf(
    "All" to "",
    "Ongoing" to "ONGOING",
    "Completed" to "COMPLETED",
    "Cancelled" to "CANCELLED",
    "Dropped" to "DROPPED",
    "Coming soon" to "COMING_SOON",
    "Mass released" to "MASS_RELEASED",
)

const val TYPE_FILTER_KEY = "seriesType"
val TYPE_OPTIONS: Options = listOf(
    "All" to "",
    "Manga" to "MANGA",
    "Manhua" to "MANHUA",
    "Manhwa" to "MANHWA",
)

const val SORT_FILTER_KEY = "orderBy"
val SORT_OPTIONS: Options = listOf(
    "Last chapter added" to "lastChapterAddedAt",
    "Views" to "totalViews",
    "Created at" to "createdAt",
    "Chapter count" to "chaptersCount",
    "Alphabetical" to "postTitle",
)

const val SORT_DIRECTION_FILTER_KEY = "orderDirection"
val SORT_DIRECTION_OPTIONS: Options = listOf(
    "Descending" to "desc",
    "Ascending" to "asc",
)

const val GENRE_INCLUDE_FILTER_KEY = "genreIds"
const val GENRE_EXCLUDE_FILTER_KEY = "excludedGenreIds"

val GENRE_OPTIONS: Options = listOf(
    "Action" to "1",
    "Drama" to "2",
    "Shounen" to "3",
    "Sports" to "4",
    "Manhwa" to "5",
    "Martial Arts" to "6",
    "Comedy" to "7",
    "Fantasy" to "8",
    "Horror" to "9",
    "Seinen" to "10",
    "Supernatural" to "11",
    "Mature" to "12",
    "Adventure" to "13",
    "Monsters" to "14",
    "System" to "15",
    "Reincarnation" to "16",
    "Revenge" to "17",
    "Slice Of Life" to "18",
    "Historical" to "19",
    "Romance" to "20",
    "Josei" to "21",
    "Shoujo" to "22",
    "School Life" to "23",
    "terror" to "24",
    "elf" to "25",
    "shojo" to "26",
    "Video Games" to "27",
    "Fantas" to "28",
    "WEB COMIC" to "29",
    "Webtoons" to "30",
    "Murim" to "31",
    "Restaurant" to "32",
    "Webtoon" to "33",
    "+100 Chapter" to "34",
    "Tower" to "35",
    "Legendary " to "36",
    "Dungeons" to "37",
    "bully" to "38",
    "orphan" to "39",
    "Sci-Fi" to "40",
    "Gore" to "41",
    "Isekai" to "42",
    "magic" to "43",
    "blood" to "44",
    "war" to "45",
    "magic and sword" to "46",
    "academy" to "47",
    "violence" to "48",
    "Harem" to "49",
    "Myth" to "50",
    "OverpoweredMC" to "51",
    "TerritoryManagement" to "52",
    "Swordsman" to "53",
    "Necromancer" to "54",
    "Mage" to "55",
    "JackOfAllTrades" to "56",
    "Artifacts" to "57",
    "CharacterGrowth" to "58",
    "Mercenary" to "59",
    "Elementals" to "60",
    "Genius" to "61",
    "Psychological" to "62",
    "Tragedy" to "63",
    "Gender Bender" to "64",
).sortedBy { it.first.trim().lowercase(Locale.ROOT) }

const val POPULAR_ORDER_BY = "totalViews"
const val LATEST_ORDER_BY = "lastChapterAddedAt"
const val ORDER_DESC = "desc"

const val SHOW_LOCKED_CHAPTERS_PREF_KEY = "pref_show_locked_chapters"
const val SHOW_LOCKED_CHAPTERS_DEFAULT = false

const val MISSING_TITLE_MESSAGE = "Series title is missing"
const val LOCKED_CHAPTER_MESSAGE = "Unlock chapter in WebView"
