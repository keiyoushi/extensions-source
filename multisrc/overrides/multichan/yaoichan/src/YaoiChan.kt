package eu.kanade.tachiyomi.extension.ru.yaoichan

import eu.kanade.tachiyomi.multisrc.multichan.MultiChan
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request

class YaoiChan : MultiChan("YaoiChan", "https://yaoi-chan.me", "ru") {

    override val id: Long = 2466512768990363955

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/?do=search&subaction=search&story=$query&search_start=$page"
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
                    else -> {}
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
                        else -> {}
                    }
                }
                if (statusParam) {
                    "$baseUrl/tags/${genres.dropLast(1)}$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/tags/$status/${genres.dropLast(1)}/$order?offset=${20 * (page - 1)}"
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
                        else -> {}
                    }
                }
                if (statusParam) {
                    "$baseUrl/$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/$order/$status?offset=${20 * (page - 1)}"
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
        Genre("18 плюс"),
        Genre("bdsm"),
        Genre("арт"),
        Genre("бара"),
        Genre("боевик"),
        Genre("боевые искусства"),
        Genre("вампиры"),
        Genre("веб"),
        Genre("гарем"),
        Genre("гендерная интрига"),
        Genre("героическое фэнтези"),
        Genre("групповой секс"),
        Genre("детектив"),
        Genre("дзёсэй"),
        Genre("додзинси"),
        Genre("драма"),
        Genre("игра"),
        Genre("инцест"),
        Genre("искусство"),
        Genre("история"),
        Genre("киберпанк"),
        Genre("комедия"),
        Genre("литРПГ"),
        Genre("махо-сёдзё"),
        Genre("меха"),
        Genre("мистика"),
        Genre("мужская беременность"),
        Genre("музыка"),
        Genre("научная фантастика"),
        Genre("омегаверс"),
        Genre("переодевание"),
        Genre("повседневность"),
        Genre("постапокалиптика"),
        Genre("приключения"),
        Genre("психология"),
        Genre("романтика"),
        Genre("самурайский боевик"),
        Genre("сборник"),
        Genre("сверхъестественное"),
        Genre("сетакон"),
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
        Genre("юмор"),
        Genre("юри"),
        Genre("яой"),
        Genre("ёнкома"),
    )
}
