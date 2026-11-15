package eu.kanade.tachiyomi.extension.tr.holyscans

import eu.kanade.tachiyomi.source.model.Filter

class Genre(name: String, val id: String) : Filter.CheckBox(name)
class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Türler", genres)

class Type(name: String, val id: String) : Filter.CheckBox(name)
class TypeFilter(types: List<Type>) : Filter.Group<Type>("Seri Tipi", types)

class Status(name: String, val id: String) : Filter.CheckBox(name)
class StatusFilter(statuses: List<Status>) : Filter.Group<Status>("Durum", statuses)

fun getGenreList() = listOf(
    Genre("Aksiyon", "aksiyon"),
    Genre("Doğaüstü", "dogaustu"),
    Genre("Dövüş Sanatları", "dovus-sanatlari"),
    Genre("Dram", "dram"),
    Genre("Fantazi", "fantazi"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Gizem", "gizem"),
    Genre("Harem", "harem"),
    Genre("İsekai", "isekai"),
    Genre("Josei", "josei"),
    Genre("Kız Aşkı", "kiz-aski"),
    Genre("Komedi", "komedi"),
    Genre("Macera", "macera"),
    Genre("Okul", "okul"),
    Genre("Romantizm", "romantizm"),
    Genre("Seinen", "seinen"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Slice Of Life", "slice-of-life"),
    Genre("Spor", "spor"),
    Genre("Tarihi", "tarihi"),
    Genre("Yaoi", "yaoi"),
    Genre("Yetişkin", "yetiskin"),
)

fun getTypeList() = listOf(
    Type("Webtoon", "manhwa"),
    Type("Manga", "manga"),
    Type("Manhua", "manhua"),
    Type("Novel", "novel"),
)

fun getStatusList() = listOf(
    Status("Devam Ediyor", "ongoing"),
    Status("Tamamlandı", "completed"),
)
