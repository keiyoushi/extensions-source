package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    GenreList(getGenresList),
    TagList(getTagsList),
)

open class Tag(name: String) : Filter.CheckBox(name)

class GenreList(genres: List<Tag>) : Filter.Group<Tag>("Gêneros", genres)

class TagList(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)

private val getGenresList = listOf(
    Tag("Academia"),
    Tag("Ação"),
    Tag("ahegao"),
    Tag("Animais"),
    Tag("Apocalipse"),
    Tag("Artes Maciais"),
    Tag("Aventura"),
    Tag("boquete"),
    Tag("Campus"),
    Tag("Comédia"),
    Tag("creampie"),
    Tag("Cultivação"),
    Tag("Drama"),
    Tag("Esportes"),
    Tag("Fantasia"),
    Tag("Ficção Científica"),
    Tag("Garotas Magicas"),
    Tag("grupal"),
    Tag("gyaru"),
    Tag("Harém"),
    Tag("Histórico"),
    Tag("Horror"),
    Tag("Isekai"),
    Tag("Loli"),
    Tag("Magia"),
    Tag("Masmorra"),
    Tag("Milf"),
    Tag("Mistério"),
    Tag("Monstros"),
    Tag("MURIM"),
    Tag("Oriental"),
    Tag("Peitões"),
    Tag("Psicológico"),
    Tag("Psychological"),
    Tag("Regressão"),
    Tag("Romance"),
    Tag("Seinen"),
    Tag("Shojo"),
    Tag("Shonen"),
    Tag("Sistema"),
    Tag("Slice of Life"),
    Tag("Supernatural"),
    Tag("Terror"),
    Tag("Vida Escolar"),
    Tag("Vingança"),
    Tag("Zumbi"),
)

private val getTagsList = listOf(
    Tag("Doujin"),
    Tag("Ecchi"),
    Tag("Loli"),
    Tag("Manga"),
    Tag("Manhua"),
    Tag("Manhwa"),
    Tag("Sugestivo"),
)
