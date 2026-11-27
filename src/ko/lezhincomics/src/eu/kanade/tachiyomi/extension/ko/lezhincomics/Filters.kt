package eu.kanade.tachiyomi.extension.ko.lezhincomics

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(name: String, private val options: List<Pair<String, String>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    val value get() = options[state].second
}

class ListOption(val name: String, val path: String, vararg val params: Pair<String, String>)

class ListFilter : Filter.Select<String>("목록", OPTIONS.map { it.name }.toTypedArray()) {
    val selected get() = OPTIONS[state]

    companion object {
        private val OPTIONS = listOf(
            ListOption("실시간 랭킹", "ranking", "rankType" to "realtime", "filter" to "all"),
            ListOption("신작 랭킹", "ranking", "rankType" to "realtime", "filter" to "new"),
            ListOption("이벤트 랭킹", "ranking", "rankType" to "realtime", "filter" to "event"),
            ListOption("오리지널", "weekday", "filter" to "original"),
            ListOption("신작", "weekday", "filter" to "new"),
            ListOption("월", "weekday", "filter" to "mon"),
            ListOption("화", "weekday", "filter" to "tue"),
            ListOption("수", "weekday", "filter" to "wed"),
            ListOption("목", "weekday", "filter" to "thu"),
            ListOption("금", "weekday", "filter" to "fri"),
            ListOption("토", "weekday", "filter" to "sat"),
            ListOption("일", "weekday", "filter" to "sun"),
            ListOption("열흘", "weekday", "filter" to "day_10"),
            ListOption("완결", "weekday", "filter" to "completed"),
            ListOption("무료", "free"),
            ListOption("만화 신작", "dynamic", "name" to "comic_new"),
        )
        val POPULAR = OPTIONS[0]
        val LATEST = OPTIONS[3]
    }
}

class GenreFilter(genres: List<GenreDto>) : SelectFilter("장르 (랭킹)", listOf("전체" to "") + genres.map { it.label to it.id })

class CompletedFilter : Filter.CheckBox("완결만 보기 (검색)")
