package eu.kanade.tachiyomi.extension.en.roliascan

import eu.kanade.tachiyomi.multisrc.mangataro.BrowseManga
import eu.kanade.tachiyomi.multisrc.mangataro.MangaTaro
import eu.kanade.tachiyomi.multisrc.mangataro.SearchWithFilters
import eu.kanade.tachiyomi.multisrc.mangataro.SortFilter
import eu.kanade.tachiyomi.multisrc.mangataro.StatusFilter
import eu.kanade.tachiyomi.multisrc.mangataro.TagFilter
import eu.kanade.tachiyomi.multisrc.mangataro.TagFilterMatch
import eu.kanade.tachiyomi.multisrc.mangataro.TypeFilter
import eu.kanade.tachiyomi.multisrc.mangataro.YearFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import rx.Observable

class RoliaScan : MangaTaro("Rolia Scan", "https://roliascan.com", "en") {

    // ========================== Search =========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            return super.fetchSearchManga(page, query, filters)
        }
        return fetchMultiplePages(page) { searchMangaRequest(it, query, filters) }
    }

    // ========================== Latest =========================
    // RoliaScan's API returns chapter-level entries with
    // blank URLs mixed in with actual manga entries.
    // Aggregate results from multiple API pages so
    // the user always gets a full page of results.
    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchMultiplePages(page) { searchMangaRequest(it, "", SortFilter.latest) }

    // ========================= Filters =========================
    override fun getFilterList() = FilterList(
        SearchWithFilters(),
        Filter.Header("If unchecked, all filters will be ignored with search query"),
        Filter.Header("But will give more relevant results"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        TagFilter(roliaTags),
        TagFilterMatch(),
    )

    // ========================= Helpers =========================
    private fun fetchMultiplePages(
        page: Int,
        requestFactory: (apiPage: Int) -> okhttp3.Request,
    ): Observable<MangasPage> {
        val startApiPage = (page - 1) * API_PAGES_PER_PAGE + 1
        val endApiPage = startApiPage + API_PAGES_PER_PAGE - 1

        return Observable.fromCallable {
            val allMangas = mutableListOf<SManga>()
            val seenIds = mutableSetOf<String>()
            var lastRawSize = 0

            for (apiPage in startApiPage..endApiPage) {
                val request = requestFactory(apiPage)
                val response = client.newCall(request).execute()
                val data = response.parseAs<List<BrowseManga>>()
                lastRawSize = data.size

                data.filter { it.type != "Novel" && it.url.isNotBlank() }
                    .forEach {
                        if (seenIds.add(it.id)) {
                            allMangas.add(browseMangaToSManga(it))
                        }
                    }

                if (data.size < 24) break
            }

            MangasPage(
                mangas = allMangas,
                hasNextPage = lastRawSize == 24,
            )
        }
    }

    companion object {
        private const val API_PAGES_PER_PAGE = 5
    }
}

private val roliaTags = listOf(
    "Action" to 5,
    "Adaptation" to 49,
    "Adapted to Manhua" to 717,
    "Adult Cast" to 119,
    "Adventure" to 19,
    "Aliens" to 803,
    "Animals" to 240,
    "Award Winning" to 8,
    "Childcare" to 1146,
    "Combat Sports" to 358,
    "Comedy" to 61,
    "Cooking" to 266,
    "Crime" to 248,
    "Crossdressing" to 724,
    "Delinquents" to 228,
    "Demons" to 162,
    "Detective" to 150,
    "Drama" to 26,
    "Ecchi" to 117,
    "Erotica" to 202,
    "Fantasy" to 17,
    "Full Color" to 40,
    "Gag Humor" to 1068,
    "Game" to 1130,
    "Gender Bender" to 1190,
    "Ghosts" to 215,
    "Gore" to 187,
    "Gourmet" to 89,
    "Harem" to 47,
    "Historical" to 66,
    "Horror" to 67,
    "Isekai" to 55,
    "Josei" to 1062,
    "Light Novel" to 98,
    "Long Strip" to 41,
    "Love Status Quo" to 541,
    "Mafia" to 356,
    "Magic" to 45,
    "Magical Sex Shift" to 551,
    "Manga" to 97,
    "Manhua" to 35,
    "Manhwa" to 18,
    "Martial Arts" to 56,
    "Mature" to 404,
    "Mecha" to 396,
    "Medical" to 244,
    "Military" to 131,
    "Monster Girls" to 231,
    "Monsters" to 46,
    "Music" to 694,
    "Mystery" to 34,
    "Mythology" to 110,
    "Ninja" to 163,
    "Office Workers" to 505,
    "Official Colored" to 866,
    "Organized Crime" to 134,
    "Otaku Culture" to 570,
    "Parody" to 605,
    "Philosophical" to 912,
    "Post-Apocalyptic" to 241,
    "Psychological" to 149,
    "Regression" to 1131,
    "Reincarnation" to 29,
    "Revenge" to 964,
    "Reverse Harem" to 1085,
    "Romance" to 2,
    "Romantic Subtext" to 486,
    "School" to 14,
    "School Life" to 27,
    "Sci-Fi" to 33,
    "Seinen" to 105,
    "Self-Published" to 577,
    "Sexual Violence" to 536,
    "Shoujo" to 1071,
    "Shounen" to 11,
    "Showbiz" to 429,
    "Slice of Life" to 93,
    "Smut" to 742,
    "Space" to 206,
    "Sports" to 9,
    "Streaming" to 1132,
    "Suggestive" to 1116,
    "Super Power" to 6,
    "Superhero" to 865,
    "Supernatural" to 65,
    "Survival" to 236,
    "Suspense" to 287,
    "Team Sports" to 10,
    "Thriller" to 184,
    "Time Travel" to 37,
    "Tragedy" to 316,
    "Transmigiration" to 1133,
    "Urban Fantasy" to 120,
    "Vampire" to 209,
    "Video Game" to 277,
    "Video Games" to 616,
    "Villainess" to 355,
    "Virtual Reality" to 617,
    "Web Comic" to 48,
    "Webtoon" to 350,
    "Workplace" to 138,
    "Wuxia" to 68,
    "Xianxia" to 718,
    "Zombies" to 1115,
)
