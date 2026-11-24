package eu.kanade.tachiyomi.extension.ru.desu

import eu.kanade.tachiyomi.source.model.Filter

// Порядок як на сайті: по добавлению / по алфавиту / по популярности / по обновлению
internal val ORDER_LABELS = arrayOf("По добавлению", "По алфавиту", "По популярности", "По обновлению")
internal val ORDER_VALUES = arrayOf("id", "name", "popular", "updated")
internal const val ORDER_POPULAR = 2
internal const val ORDER_UPDATED = 3

internal class OrderBy(state: Int = ORDER_POPULAR) : Filter.Select<String>("Сортировка", ORDER_LABELS, state) {
    val value: String get() = ORDER_VALUES[state]
}

internal class Type(name: String, val id: String) : Filter.CheckBox(name)
internal class TypeList(types: List<Type>) : Filter.Group<Type>("Тип", types)

internal class Status(name: String, val id: String) : Filter.CheckBox(name)
internal class StatusList(statuses: List<Status>) : Filter.Group<Status>("Статус", statuses)

/** Жанр або тег. На сайті параметр має вигляд `id-slug` (включити) або `id-!slug` (виключити). */
internal class Genre(name: String, val id: Int, val slug: String) : Filter.TriState(name) {
    fun toParam(): String = "$id-${if (isExcluded()) "!" else ""}$slug"
}

internal class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)
internal class TagList(tags: List<Genre>) : Filter.Group<Genre>("Теги", tags)

internal fun getTypeList() = listOf(
    Type("Манга", "manga"),
    Type("Манхва", "manhwa"),
    Type("Маньхуа", "manhua"),
    Type("Ваншот", "one_shot"),
    Type("Комикс", "comics"),
)

internal fun getStatusList() = listOf(
    Status("Выходит", "ongoing"),
    Status("Издано", "released"),
    Status("Переводится", "continued"),
    Status("Завершено", "completed"),
    Status("Без 18+", "no18plus"),
)

internal fun getGenreList() = listOf(
    Genre("Безумие", 90, "Dementia"),
    Genre("Боевые искусства", 66, "Martial Arts"),
    Genre("В цвете", 70, "Color"),
    Genre("Вампиры", 64, "Vampire"),
    Genre("Веб", 84, "Web"),
    Genre("Гарем", 71, "Harem"),
    Genre("Героическое фэнтези", 92, "Heroic Fantasy"),
    Genre("Демоны", 72, "Demons"),
    Genre("Детектив", 46, "Mystery"),
    Genre("Дзёсей", 87, "Josei"),
    Genre("Драма", 50, "Drama"),
    Genre("Ёнкома", 95, "Yonkoma"),
    Genre("Игры", 79, "Game"),
    Genre("Исекай", 89, "Isekai"),
    Genre("Исторический", 69, "Historical"),
    Genre("Комедия", 49, "Comedy"),
    Genre("Космос", 85, "Space"),
    Genre("ЛитRPG", 77, "LitRPG"),
    Genre("Магия", 58, "Magic"),
    Genre("Меха", 83, "Mecha"),
    Genre("Мистика", 61, "Mystic"),
    Genre("Музыка", 78, "Music"),
    Genre("Научная фантастика", 96, "Sci-Fi"),
    Genre("Пародия", 86, "Parody"),
    Genre("Повседневность", 54, "Slice of Life"),
    Genre("Постапокалиптика", 93, "Post Apocalyptic"),
    Genre("Приключения", 68, "Adventure"),
    Genre("Психологическое", 67, "Psychological"),
    Genre("Романтика", 62, "Romance"),
    Genre("Самураи", 88, "Samurai"),
    Genre("Сверхъестественное", 48, "Supernatural"),
    Genre("Сёдзе", 63, "Shoujo"),
    Genre("Сейнен", 52, "Seinen"),
    Genre("Сёнен", 47, "Shounen"),
    Genre("Спорт", 76, "Sports"),
    Genre("Супер сила", 82, "Super Power"),
    Genre("Трагедия", 91, "Tragedy"),
    Genre("Триллер", 81, "Thriller"),
    Genre("Ужасы", 80, "Horror"),
    Genre("Фантастика", 53, "Fiction"),
    Genre("Фэнтези", 57, "Fantasy"),
    Genre("Хентай", 59, "Hentai"),
    Genre("Школа", 60, "School"),
    Genre("Экшен", 56, "Action"),
    Genre("Этти", 51, "Ecchi"),
)

internal fun getTagList() = listOf(
    Genre("Азартные игры", 142, "Gambling"),
    Genre("Алхимия", 143, "Alchemy"),
    Genre("Ангелы", 145, "Angels"),
    Genre("Антигерой", 146, "Anti-hero"),
    Genre("Антиутопия", 147, "Dystopia"),
    Genre("Апокалипсис", 148, "Apocalypse"),
    Genre("Аристократия", 243, "Aristocracy"),
    Genre("Армия", 149, "Army"),
    Genre("Артефакты", 150, "Artifacts"),
    Genre("Боги", 151, "Gods"),
    Genre("Бои на мечах", 152, "Sword Fighting"),
    Genre("Борьба за власть", 153, "Power Struggle"),
    Genre("Брат и сестра", 154, "Brother and Sister"),
    Genre("Будущее", 155, "Future"),
    Genre("Ведьма", 156, "Witch"),
    Genre("Вестерн", 157, "Western"),
    Genre("Видеоигры", 158, "Video Games"),
    Genre("Виртуальная реальность", 159, "Virtual Reality"),
    Genre("Владыка демонов", 160, "Demon Lord"),
    Genre("Военные", 161, "Military"),
    Genre("Война", 162, "War"),
    Genre("Волшебники / маги", 163, "Wizards / Mages"),
    Genre("Волшебные существа", 164, "Magical Creatures"),
    Genre("Воспоминания из другого мира", 165, "Memories from Another World"),
    Genre("Выживание", 166, "Survival"),
    Genre("ГГ женщина", 167, "Female MC"),
    Genre("ГГ имба", 168, "IMBA MC"),
    Genre("ГГ мужчина", 169, "Male MC"),
    Genre("Геймеры", 170, "Gamers"),
    Genre("Гильдии", 171, "Guilds"),
    Genre("Глупый ГГ", 172, "Stupid MC"),
    Genre("Гоблины", 173, "Goblins"),
    Genre("Горничные", 174, "Maids"),
    Genre("Гяру", 176, "Gyaru"),
    Genre("Драконы", 177, "Dragons"),
    Genre("Дружба", 178, "Friendship"),
    Genre("Жестокий мир", 179, "Cruel World"),
    Genre("Животные компаньоны", 180, "Animal Companions"),
    Genre("Завоевание мира", 181, "World Conquest"),
    Genre("Зверолюди", 182, "Beastmen"),
    Genre("Злые духи", 183, "Evil Spirits"),
    Genre("Зомби", 184, "Zombies"),
    Genre("Игровые элементы", 185, "Gaming elements"),
    Genre("Империи", 186, "Empires"),
    Genre("Квесты", 188, "Quests"),
    Genre("Кулинария", 189, "Cooking"),
    Genre("Культивирование", 190, "Cultivation"),
    Genre("Легендарное оружие", 191, "Legendary weapons"),
    Genre("Магическая академия", 193, "Magic academy"),
    Genre("Мафия", 194, "Mafia"),
    Genre("Медицина", 195, "Medicine"),
    Genre("Месть", 196, "Revenge"),
    Genre("Моли", 192, "Moli"),
    Genre("Монстродевушки", 197, "Monster girls"),
    Genre("Монстры", 198, "Monsters"),
    Genre("Мурим", 199, "Murim"),
    Genre("Навыки / способности", 200, "Skills / abilities"),
    Genre("Наёмники", 201, "Mercenaries"),
    Genre("Насилие / жестокость", 202, "Violence / cruelty"),
    Genre("Нежить", 203, "Undead"),
    Genre("Ниндзя", 204, "Ninja"),
    Genre("Обмен телами", 205, "Body swap"),
    Genre("Обратный Гарем", 206, "Reverse Harem"),
    Genre("Огнестрельное оружие", 207, "Firearms"),
    Genre("Офисные Работники", 208, "Office workers"),
    Genre("Пираты", 209, "Pirates"),
    Genre("Подземелья", 210, "Dungeons"),
    Genre("Политика", 211, "Politics"),
    Genre("Полиция", 212, "Police"),
    Genre("Полноцветный", 214, "Full color"),
    Genre("Путешествие во времени", 217, "Time travel"),
    Genre("Рабы", 218, "Slaves"),
    Genre("Разумные расы", 219, "Sentient races"),
    Genre("Ранги силы", 220, "Power ranks"),
    Genre("Реинкарнация", 221, "Reincarnation"),
    Genre("Роботы", 222, "Robots"),
    Genre("Рыцари", 223, "Knights"),
    Genre("Система", 225, "System"),
    Genre("Скрытие личности", 226, "Identity hiding"),
    Genre("Спасение мира", 227, "Saving the world"),
    Genre("Спортивное тело", 228, "Sports body"),
    Genre("Средневековье", 229, "Middle Ages"),
    Genre("Стимпанк", 230, "Steampunk"),
    Genre("Супергерои", 231, "Superheroes"),
    Genre("Традиционные игры", 232, "Traditional games"),
    Genre("Умный ГГ", 233, "Smart GG"),
    Genre("Учитель", 234, "Teacher"),
    Genre("Философия", 235, "Philosophy"),
    Genre("Хикикомори", 236, "Hikikomori"),
    Genre("Холодное оружие", 237, "Cold weapons"),
    Genre("Шантаж", 238, "Blackmail"),
    Genre("Эльфы", 239, "Elves"),
    Genre("Якудза", 240, "Yakuza"),
    Genre("Яндере", 241, "Yandere"),
    Genre("Япония", 242, "Japan"),
)
