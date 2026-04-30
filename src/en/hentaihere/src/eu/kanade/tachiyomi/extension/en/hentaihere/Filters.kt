package eu.kanade.tachiyomi.extension.en.hentaihere

import eu.kanade.tachiyomi.source.model.Filter

val sortFilterList = listOf(
    Pair("newest", "Newest"),
    Pair("most-popular", "Most Popular"),
    Pair("last-updated", "Last Updated"),
    Pair("most-viewed", "Most Viewed"),
    Pair("alphabetical", "Alphabetical"),
    Pair("", "----"),
    Pair("staff-pick", "Staff Pick"),
    Pair("last-month", "Popular (Monthly)"),
    Pair("last-week", "Popular (Weekly)"),
    Pair("yesterday", "Popular (Daily)"),
    Pair("trending", "Trending"),
)

val alphabetFilterList = listOf(
    Pair("", "All"),
    Pair("a", "A"),
    Pair("b", "B"),
    Pair("c", "C"),
    Pair("d", "D"),
    Pair("e", "E"),
    Pair("f", "F"),
    Pair("g", "G"),
    Pair("h", "H"),
    Pair("i", "I"),
    Pair("j", "J"),
    Pair("k", "K"),
    Pair("l", "L"),
    Pair("m", "M"),
    Pair("n", "N"),
    Pair("o", "O"),
    Pair("p", "P"),
    Pair("q", "Q"),
    Pair("r", "R"),
    Pair("s", "S"),
    Pair("t", "T"),
    Pair("u", "U"),
    Pair("v", "V"),
    Pair("w", "W"),
    Pair("x", "X"),
    Pair("y", "Y"),
    Pair("z", "Z"),
)

val statusFilterList = listOf(
    Pair("", "All"),
    Pair("ongoing", "Ongoing"),
    Pair("completed", "Completed"),
)

val categoryFilterList = listOf(
    Pair("", "All"),
    Pair("t34", "Adult"),
    Pair("t7", "Anal"),
    Pair("t372", "Beastiality"),
    Pair("t20", "Big Breasts"),
    Pair("t43", "Comedy"),
    Pair("t46", "Compilation"),
    Pair("t42", "Doujinshi"),
    Pair("t40", "Ecchi"),
    Pair("t6", "Fantasy"),
    Pair("t14", "Futanari"),
    Pair("t302", "Guro"),
    Pair("t31", "Harem"),
    Pair("t15", "Incest"),
    Pair("t2650", "Isekai (Otherworld)"),
    Pair("t2158", "Korean Comic"),
    Pair("t50", "Licensed"),
    Pair("t17", "Lolicon"),
    Pair("t30", "Mecha"),
    Pair("t2503", "No Penetration"),
    Pair("t33", "Oneshot"),
    Pair("t23", "Rape"),
    Pair("t567", "Reverse Harem"),
    Pair("t41", "Romance"),
    Pair("t432", "Scat"),
    Pair("t48", "School Life"),
    Pair("t5", "Sci-fi"),
    Pair("t32", "Serialized"),
    Pair("t44", "Shotacon"),
    Pair("t49", "Tragedy"),
    Pair("t47", "Uncensored"),
    Pair("t27", "Yaoi"),
    Pair("t28", "Yuri"),
)

class SortFilter(sortables: Array<String>, state: Int = 1) : Filter.Select<String>("Sort", sortables, state)

class AlphabetFilter(alphabet: Array<String>) : Filter.Select<String>("Starts With", alphabet, 0)

class StatusFilter(statuses: Array<String>) : Filter.Select<String>("Status", statuses, 0)

class CategoryFilter(categories: Array<String>) : Filter.Select<String>("Category", categories, 0)
