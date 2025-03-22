package eu.kanade.tachiyomi.extension.ru.allhentai

import android.widget.Toast
import androidx.preference.EditTextPreference
import eu.kanade.tachiyomi.multisrc.grouple.GroupLe
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.getPreferences
import okhttp3.Request

class AllHentai : GroupLe("AllHentai", "https://20.allhen.online", "ru") {
    override val id = 1809051393403180443

    private val preferences = getPreferences()

    override val baseUrl by lazy { getPrefBaseUrl() }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = super.searchMangaRequest(page, query, filters).url.newBuilder()
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(genre.id, arrayOf("=", "=in", "=ex")[genre.state])
                    }
                }

                is Category -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(
                            category.id,
                            arrayOf("=", "=in", "=ex")[category.state],
                        )
                    }
                }

                is FiltersList -> filter.state.forEach { filters ->
                    if (filters.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(filters.id, arrayOf("=", "=in", "=ex")[filters.state])
                    }
                }

                is OrderBy -> {
                    if (filter.state > 0) {
                        val sortType = arrayOf(
                            "not",
                            "year",
                            "rate",
                            "popularity",
                            "votes",
                            "created",
                            "updated",
                        )[filter.state]
                        return GET(
                            "$baseUrl/list?sortType=$sortType&offset=${70 * (page - 1)}",
                            headers,
                        )
                    }
                }

                is Tags -> {
                    if (filter.state > 0) {
                        val tagName = tagsList[filter.state].url
                        return GET("$baseUrl/list/tag/$tagName?offset=${70 * (page - 1)}", headers)
                    }
                }

                else -> {}
            }
        }
        return if (url.toString().contains("&")) {
            GET(url.toString().replace("=%3D", "="), headers)
        } else {
            popularMangaRequest(page)
        }
    }

    private class OrderBy : Filter.Select<String>(
        "Сортировка (только)",
        arrayOf(
            "Без сортировки",
            "По году",
            "По популярности",
            "Популярно сейчас",
            "По рейтингу",
            "Новинки",
            "По дате обновления",
        ),
    )

    private class Genre(name: String, val id: String) : Filter.TriState(name)

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Жанры", genres)

    private class Category(categories: List<Genre>) : Filter.Group<Genre>("Категории", categories)

    private class FiltersList(filters: List<Genre>) : Filter.Group<Genre>("Фильтры", filters)

    private class Tags(tags: Array<String>) : Filter.Select<String>("Тэг (только)", tags)

    private data class Tag(val name: String, val url: String)

    override fun getFilterList() = FilterList(
        OrderBy(),
        Tags(tagsName),
        GenreList(genreList),
        Category(categoryList),
        FiltersList(filtersList),
    )

    private val genreList = listOf(
        Genre("ahegao", "el_855"),
        Genre("анал", "el_828"),
        Genre("бдсм", "el_78"),
        Genre("без цензуры", "el_888"),
        Genre("большая грудь", "el_837"),
        Genre("большая попка", "el_3156"),
        Genre("большой член", "el_884"),
        Genre("бондаж", "el_5754"),
        Genre("в первый раз", "el_811"),
        Genre("в цвете", "el_290"),
        Genre("гарем", "el_87"),
        Genre("гендарная интрига", "el_89"),
        Genre("групповой секс", "el_88"),
        Genre("драма", "el_95"),
        Genre("зрелые женщины", "el_5679"),
        Genre("измена", "el_291"),
        Genre("изнасилование", "el_124"),
        Genre("инцест", "el_85"),
        Genre("исторический", "el_93"),
        Genre("комедия", "el_73"),
        Genre("маленькая грудь", "el_870"),
        Genre("научная фантастика", "el_76"),
        Genre("нетораре", "el_303"),
        Genre("оральный секс", "el_853"),
        Genre("романтика", "el_74"),
        Genre("тентакли", "el_69"),
        Genre("трагедия", "el_1321"),
        Genre("ужасы", "el_75"),
        Genre("футанари", "el_77"),
        Genre("фэнтези", "el_70"),
        Genre("чикан", "el_1059"),
        Genre("этти", "el_798"),
        Genre("юри", "el_84"),
        Genre("яой", "el_83"),
    )

    private val categoryList = listOf(
        Genre("3D", "el_626"),
        Genre("Анимация", "el_5777"),
        Genre("Без текста", "el_3157"),
        Genre("Порно комикс", "el_1003"),
        Genre("Порно манхва", "el_1104"),
    )

    private val filtersList = listOf(
        Genre("Высокий рейтинг", "s_high_rate"),
        Genre("Сингл", "s_single"),
        Genre("Для взрослых", "s_mature"),
        Genre("Завершенная", "s_completed"),
        Genre("Переведено", "s_translated"),
        Genre("Длинная", "s_many_chapters"),
        Genre("Ожидает загрузки", "s_wait_upload"),
        Genre("Продается", "s_sale"),
    )

    private val tagsList = listOf(
        Tag("Без тега", "not"),
        Tag("handjob", "handjob"),
        Tag("inseki", "inseki"),
        Tag("алкоголь", "alcohol"),
        Tag("андроид", "android"),
        Tag("анилингус", "anilingus"),
        Tag("бассейн", "pool"),
        Tag("без трусиков", "without_panties"),
        Tag("беременность", "pregnancy"),
        Tag("бикини", "bikini"),
        Tag("близнецы", "twins"),
        Tag("боди-арт", "body_art"),
        Tag("больница", "hospital"),
        Tag("буккакэ", "bukkake"),
        Tag("в ванной", "in_bathroom"),
        Tag("в общественном месте", "in_public_place"),
        Tag("в транспорте", "in_vehicle"),
        Tag("вампиры", "vampires"),
        Tag("вибратор", "vibrator"),
        Tag("втянутые соски", "inverted_nipples"),
        Tag("гипноз", "hypnosis"),
        Tag("глубокий минет", "deepthroat"),
        Tag("горничные", "maids"),
        Tag("горячий источник", "hot_spring"),
        Tag("гэнгбэнг", "gangbang"),
        Tag("гяру", "gyaru"),
        Tag("двойное проникновение", "double_penetration"),
        Tag("Девочки волшебницы", "magical_girl"),
        Tag("демоны", "demons"),
        Tag("дефекация", "scat"),
        Tag("дилдо", "dildo"),
        Tag("додзинси", "doujinshi"),
        Tag("домохозяйки", "housewives"),
        Tag("дыра в стене", "hole_in_the_wall"),
        Tag("жестокость", "cruelty"),
        Tag("загар", "tan_lines"),
        Tag("зомби", "zombie"),
        Tag("инопланетяне", "aliens"),
        Tag("исполнение желаний", "granting_wish"),
        Tag("камера", "camera"),
        Tag("косплей", "cosplay"),
        Tag("кремпай", "creampie"),
        Tag("куннилингус", "cunnilingus"),
        Tag("купальник", "swimsuit"),
        Tag("лактация", "lactation"),
        Tag("латекс и кожа", "latex"),
        Tag("Ломка Психики", "mind_break"),
        Tag("магия", "magic"),
        Tag("мастурбация", "masturbation"),
        Tag("медсестра", "nurse"),
        Tag("мерзкий дядька", "terrible_oyaji"),
        Tag("много девушек", "many_girls"),
        Tag("много спермы", "a_lot_of_sperm"),
        Tag("монстрдевушки", "monstergirl"),
        Tag("монстры", "monsters"),
        Tag("мужчина крепкого телосложения", "muscle_man"),
        Tag("на природе", "outside"),
        Tag("не бритая киска", "hairy_pussy"),
        Tag("не бритые подмышки", "hairy_armpits"),
        Tag("нетори", "netori"),
        Tag("нижнее бельё", "lingerie"),
        Tag("обмен партнерами", "swinging"),
        Tag("обмен телами", "body_swap"),
        Tag("обычный секс", "normal_sex"),
        Tag("огромная грудь", "super_big_boobs"),
        Tag("орки", "orcs"),
        Tag("очки", "megane"),
        Tag("пайзури", "titsfuck"),
        Tag("парень пассив", "passive_guy"),
        Tag("пацанка", "tomboy"),
        Tag("пеггинг", "pegging"),
        Tag("переодевание", "disguise"),
        Tag("пирсинг", "piercing"),
        Tag("писают", "peeing"),
        Tag("пляж", "beach"),
        Tag("повседневность", "slice_of_life"),
        Tag("повязка на глаза", "blindfold"),
        Tag("подглядывание", "peeping"),
        Tag("подчинение", "submission"),
        Tag("похищение", "kidnapping"),
        Tag("принуждение", "forced"),
        Tag("прозрачная одежда", "transparent_clothes"),
        Tag("проституция", "prostitution"),
        Tag("психические отклонения", "mental_illness"),
        Tag("публичный секс", "public_sex"),
        Tag("пьяные", "drunk"),
        Tag("рабы", "slaves"),
        Tag("рентген зрение", "x_ray"),
        Tag("сверхъестественное", "supernatural"),
        Tag("секс втроем", "threesome"),
        Tag("секс игрушки", "sex_toys"),
        Tag("сексуально возбужденная", "horny"),
        Tag("спортивная форма", "sports_uniform"),
        Tag("спящие", "sleeping"),
        Tag("страпон", "strapon"),
        Tag("Суккуб", "succubus"),
        Tag("темнокожие", "dark_skin"),
        Tag("толстушки", "fatties"),
        Tag("трап", "trap"),
        Tag("униформа", "uniform"),
        Tag("ушастые", "eared"),
        Tag("фантазии", "dreams"),
        Tag("фемдом", "femdom"),
        Tag("фестиваль", "festival"),
        Tag("фетиш", "fetish"),
        Tag("фистинг", "fisting"),
        Tag("фурри", "furry"),
        Tag("футанари имеет парня", "futanari_on_boy"),
        Tag("футджаб", "footfuck"),
        Tag("цельный купальник", "full_swimsuit"),
        Tag("цундэрэ", "tsundere"),
        Tag("чулки", "hose"),
        Tag("шалава", "slut"),
        Tag("шантаж", "blackmail"),
        Tag("эксгибиционизм", "exhibitionism"),
        Tag("эльфы", "elves"),
        Tag("яндере", "yandere"),
    )

    private val tagsName = tagsList.map {
        it.name
    }.toTypedArray()

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        super.setupPreferenceScreen(screen)
        EditTextPreference(screen.context).apply {
            key = DOMAIN_PREF
            title = DOMAIN_TITLE
            setDefaultValue(super.baseUrl)
            dialogTitle = DOMAIN_TITLE
            dialogMessage = "Default URL:\n\t${super.baseUrl}"
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(
                    screen.context,
                    "Для смены домена необходимо перезапустить приложение с полной остановкой.",
                    Toast.LENGTH_LONG,
                ).show()
                true
            }
        }.let(screen::addPreference)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(DOMAIN_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_DOMAIN_PREF, null).let { defaultBaseUrl ->
            if (defaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(DOMAIN_PREF, super.baseUrl)
                    .putString(DEFAULT_DOMAIN_PREF, super.baseUrl)
                    .apply()
            }
        }
    }

    companion object {
        private const val DOMAIN_PREF = "Домен"
        private const val DEFAULT_DOMAIN_PREF = "pref_default_domain"
        private const val DOMAIN_TITLE = "Домен"
    }
}
