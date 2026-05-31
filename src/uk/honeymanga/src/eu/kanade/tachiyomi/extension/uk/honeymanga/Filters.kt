package eu.kanade.tachiyomi.extension.uk.honeymanga

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
}

class TriStateFilter(name: String, val id: String) : Filter.TriState(name)

abstract class TriStateGroup(
    name: String,
    val options: List<String>,
) : Filter.Group<TriStateFilter>(
    name,
    options.map { TriStateFilter(it, it) },
) {
    val included get() = state.filter { it.isIncluded() }.map { it.id }.takeUnless { it.isEmpty() }
    val excluded get() = state.filter { it.isExcluded() }.map { it.id }.takeUnless { it.isEmpty() }
}

abstract class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
    defaultValue: String? = null,
) : Filter.Select<String>(
    name,
    options.map { it.first }.toTypedArray(),
    options.map { it.second }.indexOf(defaultValue).coerceAtLeast(0),
) {
    val selected get() = options[state].second.takeUnless { it.isBlank() }
}

internal class MultiValueOption(name: String, val value: String) : Filter.CheckBox(name)

internal abstract class MultiValueFilter(
    name: String,
    options: List<String>,
) : Filter.Group<MultiValueOption>(
    name = name,
    state = options.map { MultiValueOption(it, it) },
) {
    val active get() = state.filter { it.state }.map { it.value }.takeUnless { it.isEmpty() }
}

internal class OrderBy :
    OrderByFilter(
        "Сортувати за",
        listOf(
            "За оновленнями" to "lastUpdated",
            "За кількістю вподобайок" to "likes",
            "За кількістю переглядів" to "views",
        ),
        Selection(1, false),
    )

internal class GenresFilter(blockedGenres: Set<String>) : TriStateGroup("Жанри", options) {
    init {
        state.forEach { filter ->
            if (blockedGenres.contains(filter.id)) {
                filter.state = 2
            }
        }
    }
    companion object {
        val options = listOf(
            "Апокаліпсис",
            "Ваншот",
            "Вестерн",
            "Героїчне фентезі",
            "Готика",
            "Деменція",
            "Детектив",
            "Джьосей",
            "Доджінші",
            "Драма",
            "Екшн",
            "Еротика",
            "Еччі",
            "Жахи",
            "Ісекай",
            "Історія",
            "Йонкома",
            "Комедія",
            "Магія",
            "Махо-шьоджьо",
            "Махо-шьонен",
            "Меха",
            "Містика",
            "Наукова фантастика",
            "Омегаверс",
            "Пародія",
            "Повсякденність",
            "Постапокаліпсис",
            "Пригоди",
            "Психологія",
            "Романтика",
            "Сейнен",
            "Спокон",
            "Трагедія",
            "Триллер",
            "Фантастика",
            "Фентезі",
            "Філософія",
            "Шьоджьо",
            "Шьоджьо-ай",
            "Шьонен",
            "Шьонен-ай",
            "Юрі",
            "Яой",
        )
    }
}

internal class TypeFilter :
    SelectFilter(
        "Тип (відобразити)",
        listOf(
            "Всі типи" to "",
            "Артбук" to "Артбук",
            "Вебкомікс" to "Вебкомікс",
            "Графічний роман" to "Графічний роман",
            "Мальопис" to "Мальопис",
            "Манхва" to "Манхва",
            "Маньхва" to "Маньхва",
            "Манґа" to "Манґа",
            "Новела" to "Новела",
        ),
    )

internal class HideTypeFilter(blockedTypes: Set<String>) : MultiValueFilter("Тип (приховати)", options) {
    init {
        state.forEach { filter ->
            if (blockedTypes.contains(filter.value)) {
                filter.state = true
            }
        }
    }
    companion object {
        val options = listOf(
            "Артбук",
            "Вебкомікс",
            "Графічний роман",
            "Мальопис",
            "Манхва",
            "Маньхва",
            "Манґа",
            "Новела",
        )
    }
}

internal class TagsFilter :
    TriStateGroup(
        "Категорії",
        listOf(
            "Авторська робота",
            "Аристократія",
            "Артефакти",
            "Божевілля",
            "Бойові мистецтва",
            "Бої на мечах",
            "Брат і сестра",
            "Вампіри",
            "Вигодовування",
            "Виживання",
            "Війна",
            "ГГ жінка",
            "ГГ чоловік",
            "Гарем",
            "Гільдії",
            "Демони",
            "Для дітей",
            "Дружба",
            "Жорстокість",
            "Звіролюди",
            "Зміна статі",
            "Імперії",
            "Казка",
            "Космос",
            "Культивація",
            "Кіберпанк",
            "Магія",
            "Машини",
            "Медицина",
            "Музика",
            "Мурім",
            "Надприродне",
            "Неко",
            "Парапсихологія",
            "Побут",
            "Подорожі у часі",
            "Політика",
            "Помста",
            "Пригоди",
            "Підземелля",
            "Реїнкарнація",
            "Самураї",
            "Система",
            "Спорт",
            "Суперсила",
            "Тварини",
            "Фурі",
            "Чарівники",
            "Чудовиська",
            "Школа",
            "Шматочок життя",
            "Якудза",
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Статус",
        listOf(
            "Будь-який статус" to "",
            "Анонс" to "Анонс",
            "Завершено" to "Завершено",
            "Онгоінг" to "Онгоінг",
            "Покинуто" to "Покинуто",
            "Призупинено" to "Призупинено",
        ),
    )

internal class TranslationFilter :
    SelectFilter(
        "Переклад",
        listOf(
            "Будь-який статус" to "",
            "Анонс" to "Анонс",
            "Завершено" to "Завершено",
            "Перекладається" to "Перекладається",
            "Покинуто" to "Покинуто",
            "Призупинено" to "Призупинено",
        ),
    )
