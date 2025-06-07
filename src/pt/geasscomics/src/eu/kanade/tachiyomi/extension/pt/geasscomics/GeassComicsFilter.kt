package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.Filter

class GenreList(title: String, genres: List<Genre>) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id.toString()) })
class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)
class Genre(val id: Int, val name: String)

val genresList = listOf(
    Genre(10, "Isekai"),
    Genre(11, "Sistema"),
    Genre(12, "Shonen"),
    Genre(13, "Shojo"),
    Genre(14, "Seinen"),
    Genre(15, "Josei"),
    Genre(16, "Slice of Life"),
    Genre(17, "Horror"),
    Genre(18, "Fantasy"),
    Genre(19, "Romance"),
    Genre(20, "Comedia"),
    Genre(21, "Sports"),
    Genre(22, "Supernatural"),
    Genre(23, "Mystery"),
    Genre(24, "Psychological"),
    Genre(25, "Aventura"),
    Genre(26, "Adulto"),
    Genre(27, "Hentai"),
    Genre(29, "Harém"),
    Genre(30, "Ação"),
    Genre(31, "Drama"),
    Genre(32, "Escolar"),
    Genre(35, "Monstros"),
    Genre(36, "Ecchi"),
    Genre(37, "Magia"),
    Genre(38, "Demônios"),
    Genre(40, "Dungeons"),
    Genre(41, "Manga"),
    Genre(42, "Apocalipse"),
    Genre(43, "Manhwa"),
    Genre(44, "Ficção Científica"),
    Genre(45, "Sugestivo"),
    Genre(46, "Loli"),
)
