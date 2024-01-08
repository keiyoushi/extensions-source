package eu.kanade.tachiyomi.extension.ru.mangachan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class MangaChan : MultiChan("MangaChan", "https://manga-chan.me", "ru") {

    override val id: Long = 7

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var pageNum = 1
        when {
            page < 1 -> pageNum = 1
            page >= 1 -> pageNum = page
        }
        val url = if (query.isNotEmpty()) {
            "$baseUrl/?do=search&subaction=search&story=$query&search_start=$pageNum"
        } else {
            var genres = ""
            var order = ""
            var statusParam = true
            var status = ""
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach { f ->
                            if (!f.isIgnored()) {
                                genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                            }
                        }
                    }
                    is OrderBy -> {
                        if (filter.state!!.ascending && filter.state!!.index == 0) {
                            statusParam = false
                        }
                    }
                    is Status -> status = arrayOf("", "all_done", "end", "ongoing", "new_ch")[filter.state]
                    else -> continue
                }
            }

            if (genres.isNotEmpty()) {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("", "&n=favasc", "&n=abcdesc", "&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("&n=dateasc", "&n=favdesc", "&n=abcasc", "&n=chdesc")[filter.state!!.index]
                            }
                        }
                        else -> continue
                    }
                }
                if (statusParam) {
                    "$baseUrl/tags/${genres.dropLast(1)}$order?offset=${20 * (pageNum - 1)}&status=$status"
                } else {
                    "$baseUrl/tags/$status/${genres.dropLast(1)}/$order?offset=${20 * (pageNum - 1)}"
                }
            } else {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("manga/new", "manga/new&n=favasc", "manga/new&n=abcdesc", "manga/new&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("manga/new&n=dateasc", "mostfavorites", "catalog", "sortch")[filter.state!!.index]
                            }
                        }
                        else -> continue
                    }
                }
                if (statusParam) {
                    "$baseUrl/$order?offset=${20 * (pageNum - 1)}&status=$status"
                } else {
                    "$baseUrl/$order/$status?offset=${20 * (pageNum - 1)}"
                }
            }
        }
        return GET(url, headers)
    }

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.TriState(name)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Перевод завершен", "Выпуск завершен", "Онгоинг", "Новые главы"))
    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Дата", "Популярность", "Имя", "Главы"),
        Selection(1, false),
    )

    override fun getFilterList() = FilterList(
        Status(),
        OrderBy(),
        GenreList(getGenreList()),
    )

    private fun getGenreList() = listOf(
        Genre("18_плюс"),
        Genre("bdsm"),
        Genre("арт"),
        Genre("боевик"),
        Genre("боевые_искусства"),
        Genre("вампиры"),
        Genre("веб"),
        Genre("гарем"),
        Genre("гендерная_интрига"),
        Genre("героическое_фэнтези"),
        Genre("детектив"),
        Genre("дзёсэй"),
        Genre("додзинси"),
        Genre("драма"),
        Genre("игра"),
        Genre("инцест"),
        Genre("искусство"),
        Genre("история"),
        Genre("киберпанк"),
        Genre("кодомо"),
        Genre("комедия"),
        Genre("литРПГ"),
        Genre("махо-сёдзё"),
        Genre("меха"),
        Genre("мистика"),
        Genre("музыка"),
        Genre("научная_фантастика"),
        Genre("повседневность"),
        Genre("постапокалиптика"),
        Genre("приключения"),
        Genre("психология"),
        Genre("романтика"),
        Genre("самурайский_боевик"),
        Genre("сборник"),
        Genre("сверхъестественное"),
        Genre("сказка"),
        Genre("спорт"),
        Genre("супергерои"),
        Genre("сэйнэн"),
        Genre("сёдзё"),
        Genre("сёдзё-ай"),
        Genre("сёнэн"),
        Genre("сёнэн-ай"),
        Genre("тентакли"),
        Genre("трагедия"),
        Genre("триллер"),
        Genre("ужасы"),
        Genre("фантастика"),
        Genre("фурри"),
        Genre("фэнтези"),
        Genre("школа"),
        Genre("эротика"),
        Genre("юри"),
        Genre("яой"),
        Genre("ёнкома"),
    )
}
