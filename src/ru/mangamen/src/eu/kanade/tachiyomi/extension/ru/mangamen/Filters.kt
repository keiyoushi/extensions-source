package eu.kanade.tachiyomi.extension.ru.mangamen

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.HttpUrl
import java.util.Calendar

internal interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

// The site's sort dropdown lists 4 fields and a separate asc/desc choice,
// so we mirror that with two simple selects rather than the combined
// Filter.Sort which is harder to read at a glance.
internal class SortFilter :
    Filter.Select<String>("Сортировать по", SORT_OPTIONS.map { it.first }.toTypedArray(), 3),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter("sort", SORT_OPTIONS[state].second)
    }

    companion object {
        val SORT_OPTIONS = listOf(
            "Названию (A-Z)" to "name",
            "Просмотрам" to "views",
            "Дате добавления" to "created_at",
            "Дате обновления" to "last_chapter_at",
        )
    }
}

internal class DirectionFilter :
    Filter.Select<String>("Порядок", arrayOf("По убыванию", "По возрастанию")),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        builder.addQueryParameter("dir", if (state == 0) "desc" else "asc")
    }
}

// ---------------------------------------------------------------------------
// Group filters

// Lists below are the full enumerations the site embeds in window.__DATA__.
// IDs are stable site identifiers — adding a new entry just means appending a
// "name to id" pair.
internal class GenreFilter :
    Filter.Group<GenreOption>("Жанры", GENRES.map { GenreOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state == Filter.TriState.STATE_INCLUDE }
            .forEach { builder.addQueryParameter("genres[include][]", it.id) }
        state.filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .forEach { builder.addQueryParameter("genres[exclude][]", it.id) }
    }

    companion object {
        val GENRES = listOf(
            "арт" to "32",
            "боевик" to "34",
            "боевые искусства" to "35",
            "вампиры" to "36",
            "гарем" to "37",
            "героическое фэнтези" to "39",
            "детектив" to "40",
            "дзёсэй" to "41",
            "драма" to "43",
            "игра" to "44",
            "исекай" to "79",
            "история" to "45",
            "киберпанк" to "46",
            "кодомо" to "76",
            "комедия" to "47",
            "махо-сёдзё" to "48",
            "меха" to "49",
            "мистика" to "50",
            "научная фантастика" to "51",
            "омегаверс" to "77",
            "повседневность" to "52",
            "постапокалиптика" to "53",
            "приключения" to "54",
            "психология" to "55",
            "романтика" to "56",
            "самурайский боевик" to "57",
            "сверхъестественное" to "58",
            "сёдзё" to "59",
            "сёнэн" to "61",
            "спорт" to "63",
            "сэйнэн" to "64",
            "трагедия" to "65",
            "триллер" to "66",
            "ужасы" to "67",
            "фантастика" to "68",
            "фэнтези" to "69",
            "школа" to "70",
            "этти" to "72",
        )
    }
}

internal class GenreOption(name: String, val id: String) : Filter.TriState(name)

internal class TagFilter :
    Filter.Group<TagOption>("Теги", TAGS.map { TagOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state == Filter.TriState.STATE_INCLUDE }
            .forEach { builder.addQueryParameter("tags[include][]", it.id) }
        state.filter { it.state == Filter.TriState.STATE_EXCLUDE }
            .forEach { builder.addQueryParameter("tags[exclude][]", it.id) }
    }

    companion object {
        val TAGS = listOf(
            "KAMIKITA Futago" to "471",
            "Ад" to "463",
            "Азартные игры" to "304",
            "Айдолы" to "404",
            "Алкоголь" to "406",
            "Алхимия" to "225",
            "Альтернативная история" to "417",
            "Амнезия" to "338",
            "Ангелы" to "226",
            "Антигерой" to "175",
            "Антиутопия" to "227",
            "Апокалипсис" to "228",
            "Аристократия" to "470",
            "Армия" to "229",
            "Артефакты" to "230",
            "Банды" to "443",
            "Баскетбол" to "442",
            "Без текста" to "456",
            "Бейсбол" to "429",
            "Беременность" to "376",
            "Бизнес" to "422",
            "Боги" to "215",
            "Боевые искусства" to "468",
            "Бои на мечах" to "231",
            "Бокс" to "424",
            "Болезнь" to "398",
            "Борьба за власть" to "232",
            "Брак" to "475",
            "Брак по расчету" to "360",
            "Брак по расчёту" to "507",
            "Брат и сестра" to "233",
            "Броманс" to "337",
            "Будущее" to "234",
            "Вампиры" to "348",
            "Вдова/Вдовец" to "379",
            "Вдовцы" to "436",
            "Ведьма" to "357",
            "Велоспорт" to "460",
            "Вестерн" to "235",
            "Взрослая пара" to "461",
            "Видеоигры" to "185",
            "Виртуальная реальность" to "195",
            "Владыка демонов" to "236",
            "Военные" to "179",
            "Война" to "237",
            "Волейбол" to "440",
            "Волшебники" to "344",
            "Волшебники / маги" to "281",
            "Волшебные существа" to "239",
            "Воспоминания из другого мира" to "240",
            "Врачи" to "419",
            "Выживание" to "193",
            "ГГ женщина" to "243",
            "ГГ животное" to "421",
            "ГГ имба" to "291",
            "ГГ мужчина" to "244",
            "ГГ не человек" to "416",
            "Геймеры" to "241",
            "Гильдии" to "242",
            "Глубинка" to "408",
            "Глупый ГГ" to "297",
            "Гоблины" to "245",
            "Гольф" to "495",
            "Гонки" to "435",
            "Горничные" to "169",
            "Городское фэнтези" to "486",
            "Гяру" to "178",
            "Дантагава" to "474",
            "Двойники/близнецы" to "396",
            "Дворецкий" to "493",
            "Дворянство" to "346",
            "Девочки" to "480",
            "Девушки-монстры" to "501",
            "Демоны" to "151",
            "Деревня" to "370",
            "Дети" to "476",
            "Дизайн/мода" to "395",
            "Длинноволосый ГГ" to "485",
            "Долг" to "410",
            "Драконы" to "246",
            "Древний мир" to "383",
            "Дружба" to "247",
            "Друзья детства" to "401",
            "Жестокий мир" to "249",
            "Жестокое обращение с животными" to "497",
            "Жестокость" to "459",
            "Животные компаньоны" to "250",
            "Животные-компаньоны" to "358",
            "Завоевание мира" to "251",
            "Зверолюди" to "162",
            "Злые духи" to "252",
            "Знаменитый главный герой" to "385",
            "Зомби" to "149",
            "Игровые элементы" to "253",
            "Игровые элементы Система" to "472",
            "Игры разума" to "453",
            "Издевательства" to "403",
            "Измена" to "503",
            "Измена/Неверность" to "373",
            "Изобразительное искусство" to "413",
            "Империи" to "254",
            "Инопланетяне" to "380",
            "Исторические личности" to "508",
            "Итагаки Кэйсукэ" to "492",
            "Итагаки Хако" to "498",
            "Иясикэй" to "409",
            "Квесты" to "255",
            "Китай" to "427",
            "Комплекс брата" to "467",
            "Комплекс неполноценности" to "364",
            "Комплекс сестры" to "445",
            "Королевская битва" to "450",
            "Коротковолосая ГГ" to "506",
            "Космос" to "256",
            "Косплей" to "452",
            "Криминал" to "502",
            "Крутой ГГ" to "411",
            "Кулинария" to "152",
            "Культивация" to "160",
            "Культивация и перерождение" to "375",
            "Культура отаку" to "393",
            "Легендарное оружие" to "257",
            "Лоли" to "187",
            "Любовный треугольник" to "352",
            "Любовь с первого взгляда" to "361",
            "Люди с кошачьими ушами" to "415",
            "Магическая академия" to "258",
            "Магия" to "168",
            "Маджонг" to "482",
            "Мангаки" to "434",
            "Марин Китагава" to "488",
            "Мать и дочь" to "423",
            "Мать и сын" to "391",
            "Мафия" to "172",
            "Медицина" to "153",
            "Месть" to "259",
            "Мико" to "448",
            "Милые девушки делают милые вещи" to "462",
            "Монстр Девушки" to "188",
            "Монстродевушки" to "350",
            "Монстры" to "189",
            "Море" to "438",
            "Морской народ" to "437",
            "Музыка" to "190",
            "Музыкальная группа" to "407",
            "Навыки" to "339",
            "Навыки / способности" to "260",
            "Наёмники" to "261",
            "Насилие" to "341",
            "Насилие / жестокость" to "262",
            "Неблагополучные семьи" to "430",
            "Нежить" to "263",
            "Неравенство" to "451",
            "Несколько ГГ" to "412",
            "Несчастный случай" to "365",
            "Ниндзя" to "180",
            "Обещание детства" to "387",
            "Обратный Гарем" to "191",
            "Обратный гарем" to "340",
            "Обратный исэкай" to "446",
            "Огнестрельное оружие" to "264",
            "Омегаверс" to "458",
            "Оммёдзи" to "441",
            "Остров" to "356",
            "Отец и дочь" to "372",
            "Отец и сын" to "399",
            "Открытый финал" to "432",
            "Отношения между руководителем и подчинённым" to "392",
            "Офисные Работники" to "181",
            "Офисные работники" to "345",
            "Пандемия" to "397",
            "Пародия" to "265",
            "Персонажи в очках" to "484",
            "Пираты" to "371",
            "Писатель" to "477",
            "Плейбой" to "378",
            "По мотивам аниме" to "425",
            "По мотивам игры" to "405",
            "По мотивам комикса" to "509",
            "По мотивам мультфильма" to "465",
            "По мотивам романа" to "369",
            "По мотивам телесериала" to "504",
            "Повар" to "499",
            "Под одной крышей" to "418",
            "Подземелья" to "266",
            "Полиамория" to "433",
            "Политика" to "267",
            "Полиция" to "182",
            "Предательство" to "420",
            "Преступники" to "347",
            "Преступники / Криминал" to "186",
            "Призраки" to "336",
            "Призраки / Духи" to "177",
            "Путешествие во времени" to "194",
            "Развод" to "384",
            "Разница в возрасте" to "349",
            "Разумные расы" to "268",
            "Рай" to "464",
            "Ранги силы" to "248",
            "Реинкарнация" to "148",
            "Роботы" to "269",
            "Русские" to "481",
            "Рыцари" to "270",
            "Самураи" to "183",
            "Священник" to "473",
            "Сделка с демоном" to "439",
            "Секретарь" to "490",
            "Секты" to "377",
            "Сельское хозяйство" to "414",
            "Семейные ценности" to "351",
            "Семья" to "359",
            "Сёстры" to "469",
            "Система" to "271",
            "Сказки" to "457",
            "Скрытие личности" to "273",
            "Служебный роман" to "362",
            "Смерть основных персонажей" to "363",
            "Смуглый ГГ" to "479",
            "Современное фэнтези" to "483",
            "Современный мир" to "466",
            "Сокрытие личности" to "353",
            "Сосед(-ка)" to "431",
            "Сосед/ка" to "367",
            "Спасение мира" to "274",
            "Спортивное тело" to "334",
            "Средневековье" to "173",
            "Старение" to "428",
            "Стимпанк" to "272",
            "Странствия" to "390",
            "Студенты" to "388",
            "Супергерои" to "275",
            "Суперзвёзды" to "355",
            "Суперсила" to "494",
            "Сценическое искусство" to "400",
            "Тайный ребенок" to "381",
            "Тайный ребёнок" to "491",
            "Танцор" to "500",
            "Традиционные игры" to "184",
            "Убийцы" to "386",
            "Умерший член семьи" to "366",
            "Умный ГГ" to "302",
            "Университет" to "402",
            "Учитель / ученик" to "276",
            "Учитель-ученик" to "343",
            "Фальшивая романтика" to "394",
            "Феи" to "426",
            "Фей/эльфы/великаны" to "374",
            "Философия" to "277",
            "Фотограф" to "478",
            "Футбол" to "444",
            "Хикикомори" to "166",
            "Холодное оружие" to "278",
            "Хуманизация" to "487",
            "Цундэрэ" to "455",
            "Шантаж" to "279",
            "Школа-интернат" to "454",
            "Шого Тагучи" to "489",
            "Шпионы" to "368",
            "Экзорцизм" to "389",
            "Эльфы" to "216",
            "Эсперы" to "505",
            "Юристы" to "447",
            "Якудза" to "164",
            "Яндере" to "449",
            "Яндэрэ" to "496",
            "Япония" to "280",
        )
    }
}

internal class TagOption(name: String, val id: String) : Filter.TriState(name)

internal class YearMinFilter :
    Filter.Text("Год от"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.trim().toIntOrNull()
            ?.takeIf { it in 1900..currentYear() }
            ?.let { builder.addQueryParameter("year[min]", it.toString()) }
    }
}

internal class YearMaxFilter :
    Filter.Text("Год до"),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.trim().toIntOrNull()
            ?.takeIf { it in 1900..currentYear() }
            ?.let { builder.addQueryParameter("year[max]", it.toString()) }
    }
}

internal class TypeFilter :
    Filter.Group<TypeOption>("Тип", TYPES.map { TypeOption(it.first, it.second) }),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter("types[]", it.id) }
    }

    companion object {
        val TYPES = listOf(
            "Манга" to "1",
            "OEL-манга" to "4",
            "Манхва" to "5",
            "Маньхуа" to "6",
            "Руманга" to "8",
            "Комикс западный" to "9",
        )
    }
}

internal class TypeOption(name: String, val id: String) : Filter.CheckBox(name)

internal class TranslationStatusFilter :
    Filter.Group<TranslationStatusOption>(
        "Статус перевода",
        TRANSLATION_STATUSES.map { TranslationStatusOption(it.first, it.second) },
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter("status[]", it.id) }
    }

    companion object {
        val TRANSLATION_STATUSES = listOf(
            "Продолжается" to "1",
            "Завершён" to "2",
            "Заморожен" to "3",
            "Заброшен" to "4",
        )
    }
}

internal class TranslationStatusOption(name: String, val id: String) : Filter.CheckBox(name)

internal class TitleStatusFilter :
    Filter.Group<TitleStatusOption>(
        "Статус тайтла",
        TITLE_STATUSES.map { TitleStatusOption(it.first, it.second) },
    ),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach { builder.addQueryParameter("manga_status[]", it.id) }
    }

    companion object {
        val TITLE_STATUSES = listOf(
            "Онгоинг" to "1",
            "Завершён" to "2",
            "Анонс" to "3",
            "Приостановлен" to "4",
            "Выпуск прекращён" to "5",
        )
    }
}

internal class TitleStatusOption(name: String, val id: String) : Filter.CheckBox(name)

// Order intentionally matches the site filter panel:
// Sort + direction → Genres → Tags → Year → Type → Translation status → Title status.
internal fun mangaMenFilters(): FilterList = FilterList(
    SortFilter(),
    DirectionFilter(),
    Filter.Separator(),
    GenreFilter(),
    TagFilter(),
    Filter.Separator(),
    Filter.Header("Год выпуска"),
    YearMinFilter(),
    YearMaxFilter(),
    Filter.Separator(),
    TypeFilter(),
    TranslationStatusFilter(),
    TitleStatusFilter(),
)

private fun currentYear(): Int = Calendar.getInstance()[Calendar.YEAR]
