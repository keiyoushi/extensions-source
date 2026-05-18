package eu.kanade.tachiyomi.extension.ru.mangashi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

fun getMangaShiFilters(): FilterList = FilterList(
    Filter.Header("ПРИМЕЧАНИЕ: Игнорируются при текстовом поиске!"),
    Filter.Separator(),
    SortFilter(),
    StatusFilter(),
    TypeFilter(),
    YearFilter(),
    AgeRatingFilter(),
    GenreFilter(),
)

class SortFilter :
    Filter.Select<String>(
        "Сортировка",
        SORT_LABELS,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter("sort", SORT_VALUES[state])
    }

    companion object {
        private val SORT_LABELS = arrayOf(
            "По дате добавления",
            "По обновлению глав",
            "По рейтингу",
            "По популярности",
            "По количеству глав",
            "По году выпуска",
        )

        private val SORT_VALUES = arrayOf(
            "added",
            "updated",
            "rating",
            "popular",
            "chapters",
            "year",
        )
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Статус",
        STATUS_LABELS,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = STATUS_VALUES[state]
        if (value.isNotEmpty()) {
            builder.addQueryParameter("status", value)
        }
    }

    companion object {
        private val STATUS_LABELS = arrayOf("Все", "Онгоинг", "Завершён", "Хиатус")
        private val STATUS_VALUES = arrayOf("", "ONGOING", "COMPLETED", "HIATUS")
    }
}

class TypeFilter :
    Filter.Select<String>(
        "Тип",
        TYPE_LABELS,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = TYPE_VALUES[state]
        if (value.isNotEmpty()) {
            builder.addQueryParameter("type", value)
        }
    }

    companion object {
        private val TYPE_LABELS = arrayOf("Все", "Манга", "Манхва", "Маньхуа", "Западный комикс")
        private val TYPE_VALUES = arrayOf("", "MANGA", "MANHWA", "MANHUA", "WESTERN")
    }
}

class YearFilter :
    Filter.Select<String>(
        "Год",
        YEAR_LABELS,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = YEAR_VALUES[state]
        if (value.isNotEmpty()) {
            builder.addQueryParameter("year", value)
        }
    }

    companion object {
        private val YEAR_LABELS = arrayOf(
            "Все годы",
            "2026", "2025", "2024", "2023", "2022", "2021", "2020",
            "2019", "2018", "2017", "2016", "2015", "2014", "2013",
            "2012", "2010", "2005", "1998", "1997", "1989",
        )

        private val YEAR_VALUES = arrayOf(
            "",
            "2026", "2025", "2024", "2023", "2022", "2021", "2020",
            "2019", "2018", "2017", "2016", "2015", "2014", "2013",
            "2012", "2010", "2005", "1998", "1997", "1989",
        )
    }
}

class AgeRatingFilter :
    Filter.Select<String>(
        "Возрастной рейтинг",
        AGE_LABELS,
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = AGE_VALUES[state]
        if (value.isNotEmpty()) {
            builder.addQueryParameter("age_rating", value)
        }
    }

    companion object {
        private val AGE_LABELS = arrayOf("Все", "Без 18+", "Только 18+")
        private val AGE_VALUES = arrayOf("", "sfw", "adult")
    }
}

class Tag(name: String, val value: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<Tag>(
        "Жанры / Теги",
        GENRES.map { Tag(it.first, it.second) },
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { tag ->
            builder.addQueryParameter("tag", tag.value)
        }
    }

    companion object {
        private val GENRES = listOf(
            "18+" to "18",
            "Академия" to "tag-05769fe2",
            "Антигерой" to "tag-1309a7f7",
            "Антиутопия" to "tag-4a511b8e",
            "Апокалипсис" to "tag-4adee779",
            "Аристократия" to "tag-48151849",
            "Боевик" to "tag-386280c1",
            "Боевые искусства" to "tag-7701241f",
            "Вампиры" to "tag-b0396b50",
            "Военные" to "tag-ac7e464f",
            "Выживание" to "tag-a68cdc29",
            "Гарем" to "tag-c855893e",
            "ГГ женщина" to "tag-45706580",
            "ГГ имба" to "tag-41b4b132",
            "ГГ мужчина" to "tag-847d42fc",
            "Гендерная интрига" to "tag-5ce8dc5f",
            "Демоны" to "tag-36863543",
            "Детектив" to "tag-394f730e",
            "Дзёсэй" to "tag-8b857526",
            "Драма" to "tag-794fbe56",
            "Исекай" to "tag-955ea3ff",
            "Историческое" to "tag-ae9789ff",
            "Комедия" to "tag-d52b5e63",
            "Культивация" to "tag-1d537988",
            "Магия" to "tag-121431c0",
            "Мистика" to "tag-d339496a",
            "Монстры" to "tag-56ffc473",
            "Музыка" to "tag-e65ad734",
            "Научная фантастика" to "tag-8a8d71d0",
            "Повседневность" to "tag-c71e5cc0",
            "Подземелья" to "tag-8fcd09b8",
            "Приключения" to "tag-65c3e2be",
            "Психологическое" to "tag-0c5b7bff",
            "Реинкарнация" to "tag-246a4b88",
            "Романтика" to "tag-897bb76d",
            "Сверхъестественное" to "tag-e6b86e64",
            "Сёдзё" to "tag-1e57fed7",
            "Сёнэн" to "tag-b2cc99f6",
            "Сэйнэн" to "tag-5f15ec0a",
            "Спорт" to "tag-f39e647a",
            "Супер сила" to "tag-f82c854c",
            "Трагедия" to "tag-b32f1d45",
            "Триллер" to "tag-a203bb8c",
            "Ужасы" to "tag-22e77980",
            "Фантастика" to "tag-18f95076",
            "Фэнтези" to "tag-f626cde3",
            "Школа" to "tag-1de4e421",
            "Экшен" to "tag-7c95b72a",
            "Эротика" to "tag-ef731402",
            "Этти" to "tag-3588a0b8",
        )
    }
}
