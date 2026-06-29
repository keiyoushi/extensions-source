package eu.kanade.tachiyomi.extension.uk.zenko

import eu.kanade.tachiyomi.source.model.Filter

abstract class OrderByFilter(
    displayName: String,
    val options: List<Pair<String, String>>,
    state: Selection,
) : Filter.Sort(
    displayName,
    options.map { it.first }.toTypedArray(),
    state,
) {
    val selected get() = options[state!!.index].second
    val order get() = if (state!!.ascending) "ASC" else "DESC"
}

internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    values: List<Pair<String, String>>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = values.map { MultiValueOption(it.first, it.second) },
) {
    val selectedValues: List<String> get() = state.filter { it.state && it.value.isNotEmpty() }.map { it.value }
    val checked get() = selectedValues.takeIf { it.isNotEmpty() }
}

internal class MinFilter : Filter.Text("Від")
internal class MaxFilter : Filter.Text("До")

internal class YearRangeFilter :
    Filter.Group<Filter<String>>(
        name = "Дата виходу",
        state = listOf(MinFilter(), MaxFilter()),
    ) {
    val minValue: String? get() = (state[0] as MinFilter).state.takeUnless { it.isBlank() }
    val maxValue: String? get() = (state[1] as MaxFilter).state.takeUnless { it.isBlank() }
}

internal class OrderBy :
    OrderByFilter(
        "Сортувати за",
        listOf(
            "По новизні" to "createdAt",
            "За датою виходу" to "releaseYear",
            "За кількістю переглядів" to "viewsCount",
            "За кількістю вподобайок" to "likesCount",
            "За останніми оновленнями" to "lastChapterCreatedAt",
        ),
        Selection(2, false),
    )

internal class CategoryFilter(hiddenCategories: Set<String>) : MultiValueFilter("Категорії", categories) {
    init {
        if (hiddenCategories.isNotEmpty()) {
            state.forEach { filter ->
                if (!hiddenCategories.contains(filter.value)) {
                    filter.state = true
                }
            }
        }
    }
    companion object {
        val categories = listOf(
            "Мальопис" to "MANGA_UA",
            "Манґа" to "MANGA",
            "Манхва" to "MANHVA",
            "Маньхва" to "MANHUA",
            "Вебкомікс" to "WESTERN_COMICS",
            "Комікс" to "COMICS",
            "Роман" to "RANOBE",
            "Інше" to "OTHER",
        )
    }
}

internal class TranslationStatusFilter : MultiValueFilter("Статус перекладу", statuses) {
    companion object {
        val statuses = listOf(
            "Скоро" to "COMING_SOON",
            "Перекладається" to "ONGOING",
            "Призупинено" to "PAUSED",
            "Завершено" to "FINISHED",
        )
    }
}

internal class AgeFilter(blockedAge: Set<String>) : MultiValueFilter("Вікові обмеження", age) {
    init {
        if (blockedAge.isNotEmpty()) {
            state.forEach { filter ->
                if (blockedAge.contains(filter.value)) {
                    filter.state = true
                }
            }
        }
    }
    companion object {
        val age = listOf(
            "0+" to "0",
            "16+" to "16",
            "18+" to "18",
        )
    }
}

internal class GenresFilter : MultiValueFilter("Жанри", genres) {
    companion object {
        val genres = listOf(
            "Апокаліпсис" to "Апокаліпсис",
            "Бойовик" to "Бойовик",
            "Бойові мистецтва" to "Бойові мистецтва",
            "Ваншот" to "Ваншот",
            "Вестерн" to "Вестерн",
            "Героїчне фентезі" to "Героїчне фентезі",
            "Готика" to "Готика",
            "Ґідверс" to "Ґідверс",
            "Детектив" to "Детектив",
            "Джьосей" to "Джьосей",
            "Доджінші" to "Доджінші",
            "Драма" to "Драма",
            "Екшн" to "Екшн",
            "Еротика" to "Еротика",
            "Еччі" to "Еччі",
            "Жахи" to "Жахи",
            "Ісекай" to "Ісекай",
            "Історія" to "Історія",
            "Йонкома" to "Йонкома",
            "Комедія" to "Комедія",
            "Махо-шьоджьо" to "Махо-шьоджьо",
            "Махо-шьонен" to "Махо-шьонен",
            "Меха" to "Меха",
            "Містика" to "Містика",
            "Надприродне" to "Надприродне",
            "Наукова фантастика" to "Наукова фантастика",
            "Омегаверс" to "Омегаверс",
            "Пародія" to "Пародія",
            "Повсякденність" to "Повсякденність",
            "Постапокаліпсис" to "Постапокаліпсис",
            "Пригоди" to "Пригоди",
            "Психологія" to "Психологія",
            "Романтика" to "Романтика",
            "Сейнен" to "Сейнен",
            "Сентай" to "Сентай",
            "Спокон" to "Спокон",
            "Темне фентезі" to "Темне фентезі",
            "Трагедія" to "Трагедія",
            "Триллер" to "Триллер",
            "Фантастика" to "Фантастика",
            "Фентезі" to "Фентезі",
            "Філософія" to "Філософія",
            "Шьоджьо" to "Шьоджьо",
            "Шьоджьо-ай" to "Шьоджьо-ай",
            "Шьонен" to "Шьонен",
            "Шьонен-ай" to "Шьонен-ай",
            "Юрі" to "Юрі",
            "Яой" to "Яой",
        )
    }
}

internal class TagsFilter : MultiValueFilter("Теги", tags) {
    companion object {
        val tags = listOf(
            "Авторський роман" to "Авторський роман",
            "Альфа/Альфа" to "Альфа/Альфа",
            "Альфа/Бета" to "Альфа/Бета",
            "Альфа/Омега" to "Альфа/Омега",
            "Аристократія" to "Аристократія",
            "Артефакти" to "Артефакти",
            "БДСМ" to "БДСМ",
            "Бета/Альфа" to "Бета/Альфа",
            "Божевілля" to "Божевілля",
            "Бої " to "Бої ",
            "Бої на мечах" to "Бої на мечах",
            "Бойовик" to "Бойовик",
            "Вагітність" to "Вагітність",
            "Вампіри" to "Вампіри",
            "Виживання" to "Виживання",
            "Від друзів до коханців" to "Від друзів до коханців",
            "Від ненависті до кохання" to "Від ненависті до кохання",
            "Війна" to "Війна",
            "Віртуальна реальність" to "Віртуальна реальність",
            "Вороги" to "Вороги",
            "Втрата пам'яті" to "Втрата пам'яті",
            "Гарем" to "Гарем",
            "ГГ жінка" to "ГГ жінка",
            "ГГ розумний" to "ГГ розумний",
            "ГГ чоловік" to "ГГ чоловік",
            "Гендерна інтрига" to "Гендерна інтрига",
            "Гільдії" to "Гільдії",
            "Гобліни" to "Гобліни",
            "Демони" to "Демони",
            "Діти" to "Діти",
            "Для дітей" to "Для дітей",
            "Дорослі стосунки" to "Дорослі стосунки",
            "Дружба" to "Дружба",
            "Друзі дитинства" to "Друзі дитинства",
            "Екшн" to "Екшн",
            "Ельфи" to "Ельфи",
            "Епізод життя" to "Епізод життя",
            "Еспер" to "Еспер",
            "Жорстокість" to "Жорстокість",
            "Західний сетинг" to "Західний сетинг",
            "Звіролюди" to "Звіролюди",
            "Злочин" to "Злочин",
            "Зміна статі" to "Зміна статі",
            "Ігри" to "Ігри",
            "Імперії" to "Імперії",
            "Казка" to "Казка",
            "Кіберпанк" to "Кіберпанк",
            "Космос" to "Космос",
            "Кохання" to "Кохання",
            "Кулінарія" to "Кулінарія",
            "Культивація" to "Культивація",
            "Лікарня" to "Лікарня",
            "Любовний трикутник" to "Любовний трикутник",
            "Магія" to "Магія",
            "Мафія" to "Мафія",
            "Медицина" to "Медицина",
            "Міфічні істоти" to "Міфічні істоти",
            "Молодший семе" to "Молодший семе",
            "Молодший уке" to "Молодший уке",
            "Монстри" to "Монстри",
            "Музика" to "Музика",
            "Насилля" to "Насилля",
            "Нерозділене кохання" to "Нерозділене кохання",
            "Нещасливий фінал" to "Нещасливий фінал",
            "Обмін тілами" to "Обмін тілами",
            "Одержимість " to "Одержимість ",
            "Однолітки" to "Однолітки",
            "Омега/Бета" to "Омега/Бета",
            "Омега/Омега" to "Омега/Омега",
            "Палкий секс" to "Палкий секс",
            "Парапсихологія" to "Парапсихологія",
            "Перше кохання" to "Перше кохання",
            "Підземелля" to "Підземелля",
            "Побут" to "Побут",
            "Подорожі у часі" to "Подорожі у часі",
            "Політика" to "Політика",
            "Поліція" to "Поліція",
            "Пригоди" to "Пригоди",
            "Реінкарнація" to "Реінкарнація",
            "Різниця у віці" to "Різниця у віці",
            "Різниця у розмірах" to "Різниця у розмірах",
            "Рольові ігри" to "Рольові ігри",
            "Самураї" to "Самураї",
            "Система" to "Система",
            "Спорт" to "Спорт",
            "Старший семе" to "Старший семе",
            "Старший уке" to "Старший уке",
            "Суперсила" to "Суперсила",
            "Сучасність" to "Сучасність",
            "Східний сетинг" to "Східний сетинг",
            "Тварини" to "Тварини",
            "Університет" to "Університет",
            "Фетиш" to "Фетиш",
            "Цундере" to "Цундере",
            "Чарівники" to "Чарівники",
            "Чоловіча вагітність" to "Чоловіча вагітність",
            "Чудовиська" to "Чудовиська",
            "Школа" to "Школа",
            "Шоу-бізнес" to "Шоу-бізнес",
            "Щасливий фінал" to "Щасливий фінал",
            "Якудза" to "Якудза",
            "Яндере" to "Яндере",
        )
    }
}
