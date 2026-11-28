package eu.kanade.tachiyomi.extension.ja.lezhincomics

import eu.kanade.tachiyomi.source.model.Filter

class ListOption(val name: String, val ranking: String? = null, val weekDay: String? = null)

val LIST_OPTIONS = listOf(
    ListOption("デイリーランキング", ranking = "daily"),
    ListOption("月間ランキング", ranking = "monthly"),
    ListOption("月曜連載", weekDay = "mon"),
    ListOption("火曜連載", weekDay = "tue"),
    ListOption("水曜連載", weekDay = "wed"),
    ListOption("木曜連載", weekDay = "thu"),
    ListOption("金曜連載", weekDay = "fri"),
    ListOption("土曜連載", weekDay = "sat"),
    ListOption("日曜連載", weekDay = "sun"),
)

class ListFilter : Filter.Select<String>("一覧", LIST_OPTIONS.map { it.name }.toTypedArray()) {
    val selected get() = LIST_OPTIONS[state]
}

class GenreFilter(private val genres: List<GenreDto>) : Filter.Select<String>("ランキングのジャンル", genres.map { it.name }.toTypedArray()) {
    val hashId get() = genres[state].hashId
}

class SearchTypeFilter :
    Filter.Select<String>(
        "検索対象",
        SEARCH_TYPES.map { it.first }.toTypedArray(),
    ) {
    val value get() = SEARCH_TYPES[state].second
}

private val SEARCH_TYPES = listOf(
    "すべて" to "all",
    "作品名" to "comic",
    "作家名" to "author",
    "出版社" to "publisher",
    "タグ" to "tag",
)
