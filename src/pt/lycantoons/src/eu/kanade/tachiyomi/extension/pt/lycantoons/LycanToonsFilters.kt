package eu.kanade.tachiyomi.extension.pt.lycantoons

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal val tagMapping = mapOf(
    "action" to "Ação",
    "adventure" to "Aventura",
    "comedy" to "Comédia",
    "drama" to "Drama",
    "fantasy" to "Fantasia",
    "horror" to "Horror",
    "mystery" to "Mistério",
    "romance" to "Romance",
    "school-life" to "Vida Escolar",
    "sci-fi" to "Ficção Científica",
    "slice-of-life" to "Slice of Life",
    "sports" to "Esportes",
    "supernatural" to "Sobrenatural",
    "thriller" to "Thriller",
    "tragedy" to "Tragédia",
    "seinen" to "Seinen",
    "shounen" to "Shounen",
    "shoujo" to "Shoujo",
    "josei" to "Josei",
    "harem" to "Harem",
    "reverse-harem" to "Harem Reverso",
    "ecchi" to "Ecchi",
    "yaoi" to "Yaoi",
    "yuri" to "Yuri",
    "martial-arts" to "Artes Marciais",
    "wuxia" to "Wuxia",
    "xianxia" to "Xianxia",
    "xuanhuan" to "Xuanhuan",
    "murim" to "Murim",
    "cultivation" to "Cultivação",
    "isekai" to "Isekai",
    "system" to "Sistema",
    "game" to "Game",
    "dungeon" to "Dungeon",
    "gate" to "Gate",
    "constellation" to "Constelação",
    "reincarnation" to "Reencarnação",
    "regression" to "Regressão",
    "returned-hero" to "Herói Retornado",
    "overpowered-mc" to "MC Apelão",
    "weak-to-strong" to "Fraco ao Forte",
    "historical" to "Histórico",
    "post-apocalyptic" to "Pós-Apocalíptico",
    "revenge" to "Vingança",
    "survival" to "Sobrevivência",
    "time-travel" to "Viagem no Tempo",
    "academy" to "Academia",
    "royal" to "Realeza",
    "villainess" to "Vilã",
    "gore" to "Gore",
    "tragedy-dark" to "Sangue/Violência",
    "vampire" to "Vampiro",
    "zombie" to "Zumbi",
    "demons" to "Demônios",
    "mecha" to "Mecha",
    "military" to "Militar",
    "magic" to "Magia",
    "necromancer" to "Necromante",
    "monster-taming" to "Doma de Monstros",
    "business" to "Negócios",
    "cooking" to "Culinária",
    "medical" to "Médico",
    "music" to "Música",
    "police" to "Polícia",
    "parody" to "Paródia",
)

class SeriesTypeFilter :
    ChoiceFilter(
        "Tipo",
        arrayOf(
            "" to "Todos",
            "MANGA" to "Mangá",
            "MANHWA" to "Manhwa",
            "MANHUA" to "Manhua",
            "COMIC" to "Comic",
            "WEBTOON" to "Webtoon",
        ),
    )

class StatusFilter :
    ChoiceFilter(
        "Status",
        arrayOf(
            "" to "Todos",
            "ONGOING" to "Em andamento",
            "COMPLETED" to "Completo",
            "HIATUS" to "Hiato",
            "CANCELLED" to "Cancelado",
        ),
    )

open class ChoiceFilter(
    name: String,
    private val entries: Array<Pair<String, String>>,
) : Filter.Select<String>(
    name,
    entries.map { it.second }.toTypedArray(),
) {
    fun getValue(): String = entries[state].first
}

class TagsFilter :
    Filter.Group<TagCheckBox>(
        "Tags",
        tagMapping.map { TagCheckBox(it.value, it.key) },
    )

class TagCheckBox(
    name: String,
    val value: String,
) : Filter.CheckBox(name)

inline fun <reified T : ChoiceFilter> FilterList.valueOrEmpty(): String = firstInstanceOrNull<T>()?.getValue().orEmpty()

fun FilterList.selectedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state }
    ?.map { it.value }
    .orEmpty()

object LycanToonsFilters {
    fun get(): FilterList = FilterList(
        SeriesTypeFilter(),
        StatusFilter(),
        TagsFilter(),
    )
}
