package eu.kanade.tachiyomi.extension.en.comickiba

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class Manhuagold : MangaReader(
    "Manhuagold",
    "https://manhuagold.top",
    "en",
    "views"
) {

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // =============================== Pages ================================

    override val pageListParseSelector = "div"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val script = document.selectFirst("script:containsData(const CHAPTER_ID)")!!.data()
        val id = script.substringAfter("const CHAPTER_ID = ").substringBefore(";")

        val ajaxHeaders = super.headersBuilder().apply {
            add("Accept", "application/json, text/javascript, */*; q=0.01")
            add("Referer", response.request.url.toString())
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val ajaxUrl = "$baseUrl/ajax/image/list/chap/$id"

        val ajaxResponse = client.newCall(GET(ajaxUrl, ajaxHeaders)).execute()
        return super.pageListParse(ajaxResponse)
    }

    // =============================== Filters ==============================

    override val sortFilterValues = arrayOf(
        Pair("Default", "default"),
        Pair("Latest Updated", "latest-updated"),
        Pair("Most Viewed", "views"),
        Pair("Most Viewed Month", "views_month"),
        Pair("Most Viewed Week", "views_week"),
        Pair("Most Viewed Day", "views_day"),
        Pair("Score", "score"),
        Pair("Name A-Z", "az"),
        Pair("Name Z-A", "za"),
        Pair("The highest chapter count", "chapters"),
        Pair("Newest", "new"),
        Pair("Oldest", "old"),
    )

    class StatusFilter : UriPartFilter(
        "Status",
        "status",
        arrayOf(
            Pair("All", ""),
            Pair("Completed", "completed"),
            Pair("OnGoing", "on-going"),
            Pair("On-Hold", "on-hold"),
            Pair("Canceled", "canceled"),
        ),
    )

    class GenreFilter : UriMultiSelectFilter(
        "Genres",
        "genres",
        arrayOf(
            Pair("Action", "37"),
            Pair("Adaptation", "19"),
            Pair("Adult", "5310"),
            Pair("Adventure", "38"),
            Pair("Aliens", "5436"),
            Pair("Animals", "1552"),
            Pair("Award Winning", "39"),
            Pair("Comedy", "202"),
            Pair("Comic", "287"),
            Pair("Cooking", "277"),
            Pair("Crime", "2723"),
            Pair("Delinquents", "4438"),
            Pair("Demons", "379"),
            Pair("Drama", "3"),
            Pair("Ecchi", "17"),
            Pair("Fantasy", "197"),
            Pair("Full Color", "13"),
            Pair("Gender Bender", "221"),
            Pair("Genderswap", "2290"),
            Pair("Ghosts", "2866"),
            Pair("Gore", "42"),
            Pair("Harem", "222"),
            Pair("Historical", "4"),
            Pair("Horror", "5"),
            Pair("Isekai", "259"),
            Pair("Josei", "292"),
            Pair("Loli", "5449"),
            Pair("Long Strip", "7"),
            Pair("Magic", "272"),
            Pair("Manhwa", "266"),
            Pair("Martial Arts", "40"),
            Pair("Mature", "5311"),
            Pair("Mecha", "2830"),
            Pair("Medical", "1598"),
            Pair("Military", "43"),
            Pair("Monster Girls", "2307"),
            Pair("Monsters", "298"),
            Pair("Music", "3182"),
            Pair("Mystery", "6"),
            Pair("Office Workers", "14"),
            Pair("Official Colored", "1046"),
            Pair("Philosophical", "2776"),
            Pair("Post-Apocalyptic", "1059"),
            Pair("Psychological", "493"),
            Pair("Reincarnation", "204"),
            Pair("Reverse", "280"),
            Pair("Reverse Harem", "199"),
            Pair("Romance", "186"),
            Pair("School Life", "601"),
            Pair("Sci-Fi", "1845"),
            Pair("Sexual Violence", "731"),
            Pair("Shoujo", "254"),
            Pair("Slice of Life", "10"),
            Pair("Sports", "4066"),
            Pair("Superhero", "481"),
            Pair("Supernatural", "198"),
            Pair("Survival", "44"),
            Pair("Thriller", "1058"),
            Pair("Time Travel", "299"),
            Pair("Tragedy", "41"),
            Pair("Video Games", "1846"),
            Pair("Villainess", "278"),
            Pair("Virtual Reality", "1847"),
            Pair("Web Comic", "12"),
            Pair("Webtoon", "279"),
            Pair("Webtoons", "267"),
            Pair("Wuxia", "203"),
            Pair("Yaoi", "18"),
            Pair("Yuri", "11"),
            Pair("Zombies", "1060"),
        ),
        ",",
    )

    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        StatusFilter(),
        getSortFilter(),
        GenreFilter(),
    )
}
