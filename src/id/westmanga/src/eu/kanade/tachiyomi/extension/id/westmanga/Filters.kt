package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UrlFilter {
    fun addToUrl(url: HttpUrl.Builder)
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    private val queryParameterName: String,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
),
    UrlFilter {
    private val selected get() = options[state].second

    override fun addToUrl(url: HttpUrl.Builder) {
        url.addQueryParameter(queryParameterName, selected)
    }
}

class CheckBoxFilter(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckBoxGroup(
    name: String,
    options: List<Pair<String, String>>,
    private val queryParameterName: String,
) : Filter.Group<CheckBoxFilter>(
    name,
    options.map { CheckBoxFilter(it.first, it.second) },
),
    UrlFilter {
    private val checked get() = state.filter { it.state }.map { it.value }

    override fun addToUrl(url: HttpUrl.Builder) {
        checked.forEach {
            url.addQueryParameter(queryParameterName, it)
        }
    }
}

class SortFilter(
    defaultValue: String? = null,
) : SelectFilter(
    name = "Order",
    options = listOf(
        "Default" to "Default",
        "A-Z" to "Az",
        "Z-A" to "Za",
        "Updated" to "Update",
        "Added" to "Added",
        "Popular" to "Popular",
    ),
    queryParameterName = "orderBy",
    defaultValue = defaultValue,
) {
    companion object {
        val popular = FilterList(SortFilter("Popular"))
        val latest = FilterList(SortFilter("Update"))
    }
}

class StatusFilter :
    SelectFilter(
        name = "Status",
        options = listOf(
            "All" to "All",
            "Ongoing" to "Ongoing",
            "Completed" to "Completed",
            "Hiatus" to "Hiatus",
        ),
        queryParameterName = "status",
    )

class CountryFilter :
    SelectFilter(
        name = "Country / Type",
        options = listOf(
            "All" to "All",
            "Japan / Manga" to "JP",
            "China / Manhua" to "CN",
            "Korea / Manhwa" to "KR",
        ),
        queryParameterName = "country",
    )

class ColorFilter :
    SelectFilter(
        name = "Color",
        options = listOf(
            "All" to "All",
            "Colored" to "Colored",
            "Uncolored" to "Uncolored",
        ),
        queryParameterName = "color",
    )

class GenreFilter :
    CheckBoxGroup(
        name = "Genre",
        options = listOf(
            "4-Koma" to "344",
            "Action" to "13",
            "Adult" to "2279",
            "Adventure" to "4",
            "Anthology" to "1494",
            "Comedy" to "5",
            "Comedy. Ecchi" to "2028",
            "Cooking" to "54",
            "Crime" to "856",
            "Crossdressing" to "1306",
            "Demon" to "1318",
            "Demons" to "64",
            "Drama" to "6",
            "Ecchi" to "14",
            "Ecchi. Comedy" to "1837",
            "Fantasy" to "7",
            "Game" to "36",
            "Gender Bender" to "149",
            "Genderswap" to "157",
            "genre drama" to "1843",
            "Ghosts" to "1579",
            "Gore" to "56",
            "Gyaru" to "812",
            "Harem" to "17",
            "Historical" to "44",
            "Horror" to "211",
            "Isekai" to "20",
            "Isekai Action" to "742",
            "Josei" to "164",
            "Long Strip" to "5917",
            "Magic" to "65",
            "Magical Girls" to "1527",
            "Manga" to "268",
            "Manhua" to "32",
            "Martial Art" to "754",
            "Martial arts" to "8",
            "Mature" to "46",
            "Mecha" to "22",
            "Medical" to "704",
            "Military" to "1576",
            "mons" to "2994",
            "Monster" to "1744",
            "Monster girls" to "1714",
            "Monsters" to "91",
            "Music" to "457",
            "Mystery" to "30",
            "Ninja" to "2956",
            "Novel" to "5002",
            "Office Workers" to "1501",
            "Oneshot" to "405",
            "Philosophical" to "2894",
            "Police" to "2148",
            "Project" to "313",
            "Psychological" to "23",
            "Regression" to "5476",
            "Reincarnation" to "57",
            "Reverse Harem" to "1532",
            "Romance" to "15",
            "School" to "102",
            "School life" to "9",
            "Sci fi" to "33",
            "Seinen" to "18",
            "SeinenAction" to "1525",
            "Shotacon" to "1070",
            "Shoujo" to "110",
            "Shoujo Ai" to "113",
            "Shounen" to "10",
            "Si-fi" to "776",
            "Slice of Life" to "11",
            "Smut" to "586",
            "Sports" to "103",
            "Super Power" to "274",
            "Supernatural" to "34",
            "Survival" to "2794",
            "Suspense" to "181",
            "System" to "3088",
            "Thriller" to "170",
            "Time Travel" to "1592",
            "Tragedy" to "92",
            "Urban" to "1050",
            "Vampire" to "160",
            "Video Games" to "1093",
            "Villainess" to "2831",
            "Virtual Reality" to "2490",
            "Webtoons" to "486",
            "Yuri" to "357",
            "Zombies" to "377",
        ),
        queryParameterName = "genre[]",
    )
