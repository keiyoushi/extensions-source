package eu.kanade.tachiyomi.extension.pt.mangeek

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.utils.firstInstanceOrNull

internal class TagFilter(name: String) : Filter.CheckBox(name)

internal class TagsFilter :
    Filter.Group<TagFilter>(
        "Tags",
        TAGS.map(::TagFilter),
    )

internal fun FilterList.includedTags(): List<String> = firstInstanceOrNull<TagsFilter>()?.state
    ?.filter { it.state }
    ?.map { it.name }
    .orEmpty()

internal fun getFilters() = FilterList(TagsFilter())

private val TAGS = listOf(
    "+16",
    "+18",
    "Artes marciais",
    "Aventura",
    "Ação",
    "Comédia",
    "Culinária",
    "Demônio",
    "Doujinshi",
    "Drama",
    "Ecchi",
    "Escolar",
    "Esporte",
    "Fantasia",
    "Ficção",
    "Filosófico",
    "Hentai",
    "Histórico",
    "Horror",
    "Isekai",
    "Jogo",
    "Josei",
    "Magia",
    "Manhua",
    "Manhwa",
    "Mecha",
    "Medicina",
    "Militar",
    "Mistério",
    "Monstro",
    "Musical",
    "Ninja",
    "Novel",
    "One-shot",
    "Policial",
    "Psicológico",
    "Romance",
    "Samurai",
    "Sci-fi",
    "Seinen",
    "Shoujo",
    "Shounen",
    "Slice of life",
    "Sobrenatural",
    "Super poderes",
    "Tensei",
    "Terror",
    "Tragédia",
    "Vampiro",
    "Webtoon",
    "Zumbi",
)
