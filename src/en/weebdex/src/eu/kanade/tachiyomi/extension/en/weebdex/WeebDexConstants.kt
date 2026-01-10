package eu.kanade.tachiyomi.extension.en.weebdex

object WeebDexConstants {

    // API Base URLs
    const val API_URL = "https://api.weebdex.org"
    const val CDN_URL = "https://srv.notdelta.xyz"

    // API Endpoints
    const val API_MANGA_URL = "$API_URL/manga"

    // CDN Endpoints
    const val CDN_COVER_URL = "$CDN_URL/covers"
    const val CDN_DATA_URL = "$CDN_URL/data"

    // Rate Limit (API is 5 req/s, using conservative value)
    const val RATE_LIMIT = 3

    // Tags Map
    val tags = mapOf(
        // Formats
        "Oneshot" to "99q3m1plnt",
        "Web Comic" to "1utcekkc70",
        "Doujinshi" to "fnvjk3jg1b",
        "Adaptation" to "pbst9p8bd4",
        "Full Color" to "6amsrv3w16",
        "4-Koma" to "jnqtucy8q3",

        // Genres
        "Action" to "g0eao31zjw",
        "Adventure" to "pjl8oxd1ld",
        "Boys' Love" to "1cnfhxwshb",
        "Comedy" to "onj03z2gnf",
        "Crime" to "bwec51tbms",
        "Drama" to "00xq9oqthh",
        "Fantasy" to "3lhj8r7s6n",
        "Girls' Love" to "i9w6sjikyd",
        "Historical" to "mmf28hr2co",
        "Horror" to "rclreo8b25",
        "Magical Girls" to "hy189x450f",
        "Mystery" to "hv0hsu8kje",
        "Romance" to "o0rm4pweru",
        "Slice of Life" to "13x7xvq10k",
        "Sports" to "zsvyg4whkp",
        "Tragedy" to "85hmqw16y9",

        // Themes
        "Cooking" to "9wm2j2zl1e",
        "Crossdressing" to "arjr4qdpgc",
        "Delinquents" to "h5ioz14hix",
        "Genderswap" to "25k4gcfnfp",
        "Magic" to "evt7r78scn",
        "Monster Girls" to "ddjrvi8vsu",
        "School Life" to "hobsiukk71",
        "Shota" to "lu0sbwbs3r",
        "Supernatural" to "c4rnaci8q6",
        "Traditional Games" to "aqfqkul8rg",
        "Vampires" to "djs29flsq6",
        "Video Games" to "axstzcu7pc",
        "Office Workers" to "6uytt2873o",
        "Martial Arts" to "577a4hd52b",
        "Zombies" to "szg24cwbrm",
        "Survival" to "mt4vdanhfc",
        "Police" to "acai4usl79",
        "Mafia" to "qjuief8bi1",

        // Content Tags
        "Gore" to "hceia50cf9",
        "Sexual Violence" to "xh9k4t31ll",
    )

    // Demographics
    val demographics = listOf(
        "Any" to null,
        "Shounen" to "shounen",
        "Shoujo" to "shoujo",
        "Josei" to "josei",
        "Seinen" to "seinen",
    )

    // Publication Status
    val statusList = listOf(
        "Any" to null,
        "Ongoing" to "ongoing",
        "Completed" to "completed",
        "Hiatus" to "hiatus",
        "Cancelled" to "cancelled",
    )

    // Languages
    val langList = listOf(
        "Any" to null,
        "English" to "en",
        "Japanese" to "ja",
    )

    // Sort Options
    val sortList = listOf(
        "Views" to "views",
        "Updated At" to "updatedAt",
        "Created At" to "createdAt",
        "Chapter Update" to "lastUploadedChapterAt",
        "Title" to "title",
        "Year" to "year",
        "Rating" to "rating",
        "Follows" to "follows",
        "Chapters" to "chapters",
    )
}
