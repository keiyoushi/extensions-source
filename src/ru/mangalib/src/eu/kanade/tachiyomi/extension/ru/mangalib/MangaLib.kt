package eu.kanade.tachiyomi.extension.ru.mangalib

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaLib : LibGroup("MangaLib", "https://mangalib.me", "ru") {

    override val id: Long = 6111047689498497237

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_${id}_2", 0x0000)
    }

    private val baseOrig: String = "https://mangalib.me"
    private val baseMirr: String = "https://mangalib.org"
    private var domain: String? = preferences.getString(DOMAIN_PREF, baseOrig)
    override val baseUrl: String = domain.toString()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (csrfToken.isEmpty()) {
            val tokenResponse = client.newCall(popularMangaRequest(page)).execute()
            val resBody = tokenResponse.body.string()
            csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
        }
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is AgeList -> filter.state.forEach { age ->
                    if (age.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(
                            if (age.isIncluded()) "caution[include][]" else "caution[exclude][]",
                            age.id,
                        )
                    }
                }
                is TagList -> filter.state.forEach { tag ->
                    if (tag.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(
                            if (tag.isIncluded()) "tags[include][]" else "tags[exclude][]",
                            tag.id,
                        )
                    }
                }
                else -> {}
            }
        }
        return POST(url.toString(), catalogHeaders())
    }

    // Filters
    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)

    private class TagList(tags: List<SearchFilter>) : Filter.Group<SearchFilter>("Теги", tags)
    private class AgeList(ages: List<SearchFilter>) : Filter.Group<SearchFilter>("Возрастное ограничение", ages)

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().toMutableList()
        filters.add(4, TagList(getTagList()))
        filters.add(7, AgeList(getAgeList()))
        return FilterList(filters)
    }

    private fun getTagList() = listOf(
        SearchFilter("Азартные игры", "304"),
        SearchFilter("Алхимия", "225"),
        SearchFilter("Ангелы", "226"),
        SearchFilter("Антигерой", "175"),
        SearchFilter("Антиутопия", "227"),
        SearchFilter("Апокалипсис", "228"),
        SearchFilter("Армия", "229"),
        SearchFilter("Артефакты", "230"),
        SearchFilter("Боги", "215"),
        SearchFilter("Бои на мечах", "231"),
        SearchFilter("Борьба за власть", "231"),
        SearchFilter("Брат и сестра", "233"),
        SearchFilter("Будущее", "234"),
        SearchFilter("Ведьма", "338"),
        SearchFilter("Вестерн", "235"),
        SearchFilter("Видеоигры", "185"),
        SearchFilter("Виртуальная реальность", "195"),
        SearchFilter("Владыка демонов", "236"),
        SearchFilter("Военные", "179"),
        SearchFilter("Война", "237"),
        SearchFilter("Волшебники / маги", "281"),
        SearchFilter("Волшебные существа", "239"),
        SearchFilter("Воспоминания из другого мира", "240"),
        SearchFilter("Выживание", "193"),
        SearchFilter("ГГ женщина", "243"),
        SearchFilter("ГГ имба", "291"),
        SearchFilter("ГГ мужчина", "244"),
        SearchFilter("Геймеры", "241"),
        SearchFilter("Гильдии", "242"),
        SearchFilter("Глупый ГГ", "297"),
        SearchFilter("Гоблины", "245"),
        SearchFilter("Горничные", "169"),
        SearchFilter("Гяру", "178"),
        SearchFilter("Демоны", "151"),
        SearchFilter("Драконы", "246"),
        SearchFilter("Дружба", "247"),
        SearchFilter("Жестокий мир", "249"),
        SearchFilter("Животные компаньоны", "250"),
        SearchFilter("Завоевание мира", "251"),
        SearchFilter("Зверолюди", "162"),
        SearchFilter("Злые духи", "252"),
        SearchFilter("Зомби", "149"),
        SearchFilter("Игровые элементы", "253"),
        SearchFilter("Империи", "254"),
        SearchFilter("Квесты", "255"),
        SearchFilter("Космос", "256"),
        SearchFilter("Кулинария", "152"),
        SearchFilter("Культивация", "160"),
        SearchFilter("Легендарное оружие", "257"),
        SearchFilter("Лоли", "187"),
        SearchFilter("Магическая академия", "258"),
        SearchFilter("Магия", "168"),
        SearchFilter("Мафия", "172"),
        SearchFilter("Медицина", "153"),
        SearchFilter("Месть", "259"),
        SearchFilter("Монстр Девушки", "188"),
        SearchFilter("Монстры", "189"),
        SearchFilter("Музыка", "190"),
        SearchFilter("Навыки / способности", "260"),
        SearchFilter("Насилие / жестокость", "262"),
        SearchFilter("Наёмники", "261"),
        SearchFilter("Нежить", "263"),
        SearchFilter("Ниндая", "180"),
        SearchFilter("Обратный Гарем", "191"),
        SearchFilter("Огнестрельное оружие", "264"),
        SearchFilter("Офисные Работники", "181"),
        SearchFilter("Пародия", "265"),
        SearchFilter("Пираты", "340"),
        SearchFilter("Подземелья", "266"),
        SearchFilter("Политика", "267"),
        SearchFilter("Полиция", "182"),
        SearchFilter("Преступники / Криминал", "186"),
        SearchFilter("Призраки / Духи", "177"),
        SearchFilter("Путешествие во времени", "194"),
        SearchFilter("Разумные расы", "268"),
        SearchFilter("Ранги силы", "248"),
        SearchFilter("Реинкарнация", "148"),
        SearchFilter("Роботы", "269"),
        SearchFilter("Рыцари", "270"),
        SearchFilter("Самураи", "183"),
        SearchFilter("Система", "271"),
        SearchFilter("Скрытие личности", "273"),
        SearchFilter("Спасение мира", "274"),
        SearchFilter("Спортивное тело", "334"),
        SearchFilter("Средневековье", "173"),
        SearchFilter("Стимпанк", "272"),
        SearchFilter("Супергерои", "275"),
        SearchFilter("Традиционные игры", "184"),
        SearchFilter("Умный ГГ", "302"),
        SearchFilter("Учитель / ученик", "276"),
        SearchFilter("Философия", "277"),
        SearchFilter("Хикикомори", "166"),
        SearchFilter("Холодное оружие", "278"),
        SearchFilter("Шантаж", "279"),
        SearchFilter("Эльфы", "216"),
        SearchFilter("Якудза", "164"),
        SearchFilter("Япония", "280"),

    )

    private fun getAgeList() = listOf(
        SearchFilter("Отсутствует", "0"),
        SearchFilter("16+", "1"),
        SearchFilter("18+", "2"),
    )

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"
        private const val DOMAIN_PREF = "MangaLibDomain"
        private const val DOMAIN_PREF_Title = "Выбор домена"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        ListPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = DOMAIN_PREF_Title
            entries = arrayOf("Основной (mangalib.me)", "Зеркало (mangalib.org)")
            entryValues = arrayOf(baseOrig, baseMirr)
            summary = "%s"
            setDefaultValue(baseOrig)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(DOMAIN_PREF, newValue as String).commit()
                    val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                    Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }.let(screen::addPreference)
    }
}
