package eu.kanade.tachiyomi.extension.ru.hentailib

import android.app.Application
import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.libgroup.LibGroup
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class HentaiLib : LibGroup("HentaiLib", "https://hentailib.me", "ru") {

    override val id: Long = 6425650164840473547

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private var domain: String = preferences.getString(DOMAIN_TITLE, DOMAIN_DEFAULT)!!
    override val baseUrl: String = domain

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (csrfToken.isEmpty()) {
            val tokenResponse = client.newCall(popularMangaRequest(page)).execute()
            val resBody = tokenResponse.body.string()
            csrfToken = "_token\" content=\"(.*)\"".toRegex().find(resBody)!!.groups[1]!!.value
        }
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
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

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().toMutableList()
        filters.add(4, TagList(getTagList()))
        return FilterList(filters)
    }

    private fun getTagList() = listOf(
        SearchFilter("3D", "1"),
        SearchFilter("Defloration", "287"),
        SearchFilter("FPP(Вид от первого лица)", "289"),
        SearchFilter("Footfuck", "5"),
        SearchFilter("Handjob", "6"),
        SearchFilter("Lactation", "7"),
        SearchFilter("Living clothes", "284"),
        SearchFilter("Mind break", "9"),
        SearchFilter("Scat", "13"),
        SearchFilter("Selfcest", "286"),
        SearchFilter("Shemale", "220"),
        SearchFilter("Tomboy", "14"),
        SearchFilter("Unbirth", "283"),
        SearchFilter("X-Ray", "15"),
        SearchFilter("Алкоголь", "16"),
        SearchFilter("Анал", "17"),
        SearchFilter("Андроид", "18"),
        SearchFilter("Анилингус", "19"),
        SearchFilter("Анимация (GIF)", "350"),
        SearchFilter("Арт", "20"),
        SearchFilter("Ахэгао", "2"),
        SearchFilter("БДСМ", "22"),
        SearchFilter("Бакуню", "21"),
        SearchFilter("Бара", "293"),
        SearchFilter("Без проникновения", "336"),
        SearchFilter("Без текста", "23"),
        SearchFilter("Без трусиков", "24"),
        SearchFilter("Без цензуры", "25"),
        SearchFilter("Беременность", "26"),
        SearchFilter("Бикини", "27"),
        SearchFilter("Близнецы", "28"),
        SearchFilter("Боди-арт", "29"),
        SearchFilter("Больница", "30"),
        SearchFilter("Большая грудь", "31"),
        SearchFilter("Большая попка", "32"),
        SearchFilter("Борьба", "33"),
        SearchFilter("Буккакэ", "34"),
        SearchFilter("В бассейне", "35"),
        SearchFilter("В ванной", "36"),
        SearchFilter("В государственном учреждении", "37"),
        SearchFilter("В общественном месте", "38"),
        SearchFilter("В очках", "8"),
        SearchFilter("В первый раз", "39"),
        SearchFilter("В транспорте", "40"),
        SearchFilter("Вампиры", "41"),
        SearchFilter("Вибратор", "42"),
        SearchFilter("Втроём", "43"),
        SearchFilter("Гипноз", "44"),
        SearchFilter("Глубокий минет", "45"),
        SearchFilter("Горячий источник", "46"),
        SearchFilter("Групповой секс", "47"),
        SearchFilter("Гуро", "307"),
        SearchFilter("Гяру и Гангуро", "48"),
        SearchFilter("Двойное проникновение", "49"),
        SearchFilter("Девочки-волшебницы", "50"),
        SearchFilter("Девушка-туалет", "51"),
        SearchFilter("Демон", "52"),
        SearchFilter("Дилдо", "53"),
        SearchFilter("Домохозяйка", "54"),
        SearchFilter("Дыра в стене", "55"),
        SearchFilter("Жестокость", "56"),
        SearchFilter("Золотой дождь", "57"),
        SearchFilter("Зомби", "58"),
        SearchFilter("Зоофилия", "351"),
        SearchFilter("Зрелые женщины", "59"),
        SearchFilter("Избиение", "223"),
        SearchFilter("Измена", "60"),
        SearchFilter("Изнасилование", "61"),
        SearchFilter("Инопланетяне", "62"),
        SearchFilter("Инцест", "63"),
        SearchFilter("Исполнение желаний", "64"),
        SearchFilter("Историческое", "65"),
        SearchFilter("Камера", "66"),
        SearchFilter("Кляп", "288"),
        SearchFilter("Колготки", "67"),
        SearchFilter("Косплей", "68"),
        SearchFilter("Кримпай", "3"),
        SearchFilter("Куннилингус", "69"),
        SearchFilter("Купальники", "70"),
        SearchFilter("ЛГБТ", "343"),
        SearchFilter("Латекс и кожа", "71"),
        SearchFilter("Магия", "72"),
        SearchFilter("Маленькая грудь", "73"),
        SearchFilter("Мастурбация", "74"),
        SearchFilter("Медсестра", "221"),
        SearchFilter("Мейдочка", "75"),
        SearchFilter("Мерзкий дядька", "76"),
        SearchFilter("Милф", "77"),
        SearchFilter("Много девушек", "78"),
        SearchFilter("Много спермы", "79"),
        SearchFilter("Молоко", "80"),
        SearchFilter("Монашка", "353"),
        SearchFilter("Монстродевушки", "81"),
        SearchFilter("Монстры", "82"),
        SearchFilter("Мочеиспускание", "83"),
        SearchFilter("На природе", "84"),
        SearchFilter("Наблюдение", "85"),
        SearchFilter("Насекомые", "285"),
        SearchFilter("Небритая киска", "86"),
        SearchFilter("Небритые подмышки", "87"),
        SearchFilter("Нетораре", "88"),
        SearchFilter("Нэтори", "11"),
        SearchFilter("Обмен телами", "89"),
        SearchFilter("Обычный секс", "90"),
        SearchFilter("Огромная грудь", "91"),
        SearchFilter("Огромный член", "92"),
        SearchFilter("Омораси", "93"),
        SearchFilter("Оральный секс", "94"),
        SearchFilter("Орки", "95"),
        SearchFilter("Остановка времени", "296"),
        SearchFilter("Пайзури", "96"),
        SearchFilter("Парень пассив", "97"),
        SearchFilter("Переодевание", "98"),
        SearchFilter("Пирсинг", "308"),
        SearchFilter("Пляж", "99"),
        SearchFilter("Повседневность", "100"),
        SearchFilter("Подвязки", "282"),
        SearchFilter("Подглядывание", "101"),
        SearchFilter("Подчинение", "102"),
        SearchFilter("Похищение", "103"),
        SearchFilter("Превозмогание", "104"),
        SearchFilter("Принуждение", "105"),
        SearchFilter("Прозрачная одежда", "106"),
        SearchFilter("Проституция", "107"),
        SearchFilter("Психические отклонения", "108"),
        SearchFilter("Публично", "109"),
        SearchFilter("Пытки", "224"),
        SearchFilter("Пьяные", "110"),
        SearchFilter("Рабы", "356"),
        SearchFilter("Рабыни", "111"),
        SearchFilter("С Сюжетом", "337"),
        SearchFilter("Сuminside", "4"),
        SearchFilter("Секс-игрушки", "112"),
        SearchFilter("Сексуально возбуждённая", "113"),
        SearchFilter("Сибари", "114"),
        SearchFilter("Спортивная форма", "117"),
        SearchFilter("Спортивное тело", "335"),
        SearchFilter("Спящие", "118"),
        SearchFilter("Страпон", "119"),
        SearchFilter("Суккуб", "120"),
        SearchFilter("Темнокожие", "121"),
        SearchFilter("Тентакли", "122"),
        SearchFilter("Толстушки", "123"),
        SearchFilter("Трагедия", "124"),
        SearchFilter("Трап", "125"),
        SearchFilter("Ужасы", "126"),
        SearchFilter("Униформа", "127"),
        SearchFilter("Учитель и ученик", "352"),
        SearchFilter("Ушастые", "128"),
        SearchFilter("Фантазии", "129"),
        SearchFilter("Фемдом", "130"),
        SearchFilter("Фестиваль", "131"),
        SearchFilter("Фетиш", "132"),
        SearchFilter("Фистинг", "133"),
        SearchFilter("Фурри", "134"),
        SearchFilter("Футанари", "136"),
        SearchFilter("Футанари имеет парня", "137"),
        SearchFilter("Цельный купальник", "138"),
        SearchFilter("Цундэрэ", "139"),
        SearchFilter("Чикан", "140"),
        SearchFilter("Чулки", "141"),
        SearchFilter("Шлюха", "142"),
        SearchFilter("Эксгибиционизм", "143"),
        SearchFilter("Эльф", "144"),
        SearchFilter("Юные", "145"),
        SearchFilter("Яндэрэ", "146"),
    )

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_TITLE
            this.title = DOMAIN_TITLE
            summary = domain
            this.setDefaultValue(DOMAIN_DEFAULT)
            dialogTitle = DOMAIN_TITLE
            setOnPreferenceChangeListener { _, newValue ->
                val warning = "Для смены домена необходимо перезапустить приложение с полной остановкой."
                Toast.makeText(screen.context, warning, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        const val PREFIX_SLUG_SEARCH = "slug:"

        private const val DOMAIN_TITLE = "Домен"
        private const val DOMAIN_DEFAULT = "https://hentailib.me"
    }
}
