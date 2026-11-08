package eu.kanade.tachiyomi.extension.pt.hentaiyaoi

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    Filter.Header("Os filtros são ignorados na busca por texto"),
    CategoryFilter(),
    TagFilter(),
)

open class SelectFilter(name: String, private val options: List<Pair<String, String?>>) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selected() = options[state].second
}

class CategoryFilter : SelectFilter("Categoria", listOf("Todas" to null) + CATEGORIES)

class TagFilter : SelectFilter("Tag", listOf("Todas" to null) + TAGS)

// Without the video categories (Animes Hentai, Filmes Hentai Gay)
private val CATEGORIES = listOf(
    "Cartoon Pornô" to "cartoon-porno",
    "Comics" to "comics",
    "Doujinshi" to "doujinshi",
    "Hentai" to "hentai",
    "Hentai 3D" to "hentai-3d",
    "Imagens Hentai" to "imagens",
    "Sem Censura" to "sem-censura",
)

private val TAGS = listOf(
    "Ahegao" to "ahegao",
    "Anal" to "anal",
    "Bara" to "bara",
    "Bêbado" to "bebado",
    "Beijo Grego" to "beijo-grego",
    "Bondage" to "bondage",
    "Boquete" to "boquete",
    "Brinquedos Sexuais" to "brinquedos-sexuais",
    "Camisinha" to "camisinha",
    "Colegial" to "colegial",
    "Controle da Mente" to "controle-da-mente",
    "Creampie" to "creampie",
    "Crossdressing" to "crossdressing",
    "Cuntboy" to "cuntboy",
    "Dark Skin" to "dark-skin",
    "Dedilhada" to "dedilhada",
    "Dilf" to "dilf",
    "Dupla Penetração" to "dupla-penetracao",
    "Estupro" to "estupro",
    "Exibicionismo" to "exibicionismo",
    "Femboy" to "femboy",
    "Footjob" to "footjob",
    "Furry" to "furry",
    "Futanari" to "futanari",
    "Gordinho" to "gordinho",
    "Grupal" to "grupal",
    "Incesto" to "incesto",
    "Maiô" to "maio",
    "Massagem na Próstata" to "massagem-na-prostata",
    "Masturbação" to "masturbacao",
    "Natal" to "natal",
    "Netorare" to "netorare",
    "Pau Grande" to "pau-grande",
    "Policial" to "policial",
    "Professor" to "professor",
    "Romance" to "romance",
    "Shotacon" to "shotacon",
    "Tentáculos" to "tentaculos",
    "Tomgirl" to "tomgirl",
    "Trap" to "trap",
    "Yaoi" to "yaoi",
)
