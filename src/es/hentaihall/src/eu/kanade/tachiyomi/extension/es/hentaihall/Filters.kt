package eu.kanade.tachiyomi.extension.es.hentaihall

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SearchByFilter(),
    SortFilter(),
    Filter.Separator(),
    GenreFilterGroup(),
)

class SearchByFilter : Filter.Select<String>("Buscar por", arrayOf("Nombre", "Autores")) {
    fun selectedValue() = when (state) {
        1 -> "autores"
        else -> "nombre"
    }
}

class SortFilter :
    Filter.Sort(
        "Ordenar por",
        arrayOf("Alfabético", "Creación", "Popularidad"),
        Selection(2, ascending = false),
    ) {
    fun selectedValue() = when (state?.index) {
        0 -> "alfabetico"
        1 -> "creacion"
        else -> "seguir"
    }
}

class GenreFilter(name: String) : Filter.CheckBox(name)

class GenreFilterGroup : Filter.Group<GenreFilter>("Géneros", GENRES.map { GenreFilter(it) })

private val GENRES = listOf(
    "Anal", "Ahegao", "Bbw", "Bestiality", "Big Ass", "Big Boobs", "Bisexual",
    "Blowjob", "Bondage", "Bukkake", "Cheating", "Comedy", "Colour", "Creampie",
    "Dark Skin", "Deepthroat", "Double Penetration", "Domination", "Exhibitionism",
    "Fantasy", "Femdom", "Fetish", "Ffm Threesome", "Filming", "FootJob", "Forced",
    "Furry", "Futanari", "Group", "Gyaru", "Harem", "Horror", "Humiliation",
    "Impregnation", "Incest", "Kissing", "Loli", "Mature", "Milf", "Mmf Threesome",
    "Mother", "Monsters", "Netorare", "Netorase", "Nympho", "Orgy", "Oyakodon",
    "Parody", "Pregnant", "Rape", "Romance", "Shota", "Small Boobs", "Sole Female",
    "Sole Male", "Sport", "Student", "Tall Girl", "Tentacles", "Tomboy", "Toys",
    "Tsundere", "Uncensored", "Virgin", "Yandere", "Yaoi", "Yuri", "3D",
)
