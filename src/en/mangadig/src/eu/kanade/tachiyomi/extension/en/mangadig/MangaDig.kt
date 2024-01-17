package eu.kanade.tachiyomi.extension.en.mangadig

import eu.kanade.tachiyomi.multisrc.colamanga.ColaManga
import eu.kanade.tachiyomi.source.model.FilterList

class MangaDig : ColaManga(
    "MangaDig",
    "https://mangadig.com",
    "en",
) {
    override fun popularMangaNextPageSelector() = "a:contains(Next):not(.fed-btns-disad)"

    override val statusTitle = "Status"

    override val authorTitle = "Author"

    override val genreTitle = "Category"

    override val statusOngoing = "OnGoing"

    override val statusCompleted = "Complete"

    private class StatusFilter : QueryFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "1"),
            Pair("Complete", "2"),
        ),
    )

    private class SortFilter : QueryFilter(
        "Order by",
        "orderBy",
        arrayOf(
            Pair("Last updated", "update"),
            Pair("Recently added", "create"),
            Pair("Most popular today", "dailyCount"),
            Pair("Most popular this week", "weeklyCount"),
            Pair("Most popular this month", "monthlyCount"),
        ),
        2,
    )

    private class CategoryFilter : QueryFilter(
        "Genre",
        "mainCategoryId",
        arrayOf(
            Pair("All", ""),
            Pair("Romance", "10008"),
            Pair("Drama", "10005"),
            Pair("Comedy", "10004"),
            Pair("Fantasy", "10006"),
            Pair("Action", "10002"),
            Pair("CEO", "10142"),
            Pair("Webtoons", "10012"),
            Pair("Historical", "10021"),
            Pair("Adventure", "10003"),
            Pair("Josei", "10059"),
            Pair("Smut", "10047"),
            Pair("Supernatural", "10018"),
            Pair("School life", "10017"),
            Pair("Completed", "10423"),
            Pair("Possessive", "10284"),
            Pair("Manhua", "10010"),
            Pair("Sweet", "10282"),
            Pair("Harem", "10007"),
            Pair("Slice of life", "10026"),
            Pair("Girl Power", "10144"),
            Pair("Martial arts", "10013"),
            Pair("Chinese Classic", "10243"),
            Pair("BL", "10262"),
            Pair("Manhwa", "10039"),
            Pair("Adult", "10030"),
            Pair("Shounen", "10009"),
            Pair("TimeTravel", "10143"),
            Pair("Shoujo", "10054"),
            Pair("Ecchi", "10027"),
            Pair("Revenge", "10556"),
        ),
    )

    private class CharFilter : QueryFilter(
        "Alphabet",
        "charCategoryId",
        arrayOf(
            Pair("All", ""),
            Pair("A", "10015"),
            Pair("B", "10028"),
            Pair("C", "10055"),
            Pair("D", "10034"),
            Pair("E", "10049"),
            Pair("F", "10056"),
            Pair("G", "10023"),
            Pair("H", "10037"),
            Pair("I", "10035"),
            Pair("J", "10060"),
            Pair("K", "10022"),
            Pair("L", "10046"),
            Pair("M", "10020"),
            Pair("N", "10044"),
            Pair("O", "10024"),
            Pair("P", "10048"),
            Pair("Q", "10051"),
            Pair("R", "10025"),
            Pair("S", "10011"),
            Pair("T", "10001"),
            Pair("U", "10058"),
            Pair("V", "10016"),
            Pair("W", "10052"),
            Pair("X", "10061"),
            Pair("Y", "10036"),
            Pair("Z", "10101"),
        ),
    )

    override fun getFilterList() = FilterList(
        SortFilter(),
        CategoryFilter(),
        CharFilter(),
        StatusFilter(),
    )

    override val logTag = "mangadig"

    override val mainSiteRateLimitPrefTitle = "Rate limit"

    override val mainSiteRateLimitPrefSummary = "This value affects the number of requests sent to the website. Lowering this value may reduce HTTP 403 errors, but loading times will also be slower. Restart Tachiyomi to apply changes.\nCurrent value: %s"

    override val mainSiteRateLimitPeriodPrefTitle = "Rate limit period"

    override val mainSiteRateLimitPeriodPrefSummary = "This value affects the delay after hitting the configured rate limit. Increasing this value may reduce HTTP 403 errors, but loading times will also be slower. Restart Tachiyomi to apply changes.\nCurrent value: %s"
}
