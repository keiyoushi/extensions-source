package eu.kanade.tachiyomi.extension.tr.mangaship

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Kategoriler", genres)

class TypeFilter : Filter.Select<String>("Seri Tipi", arrayOf("Hepsi", "Manga", "Webtoon", "Manhwa", "Manhua", "Amatör")) {
    fun toUriPart() = arrayOf("", "1", "2", "3", "4", "5")[state]
}

class StatusFilter : Filter.Select<String>("Durum", arrayOf("Hepsi", "Devam Edenler", "Tamamlananlar")) {
    fun toUriPart() = arrayOf("", "1", "0")[state]
}
