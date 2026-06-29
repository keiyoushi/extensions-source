package eu.kanade.tachiyomi.extension.es.ikuhentai

import eu.kanade.tachiyomi.source.model.Filter

class TextField(name: String, val key: String) : Filter.Text(name)

class SortBy :
    UriPartFilter(
        "Ordenar por",
        arrayOf(
            Pair("Relevance", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Calificación", "rating"),
            Pair("Tendencia", "trending"),
            Pair("Más visto", "views"),
            Pair("Nuevo", "new-manga"),
        ),
    )

class Genre(name: String, val id: String = name) : Filter.TriState(name)
class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
class Status(name: String, val id: String = name) : Filter.TriState(name)
class StatusList(statuses: List<Status>) : Filter.Group<Status>("Estado", statuses)

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

internal fun getStatusList() = listOf(
    Status("Completado", "end"),
    Status("En emisión", "on-going"),
    Status("Cancelado", "canceled"),
    Status("Pausado", "on-hold"),
)

internal fun getGenreList() = listOf(
    Genre("Ahegao", "ahegao"),
    Genre("Anal", "anal"),
    Genre("Bestiality", "bestialidad"),
    Genre("Bondage", "bondage"),
    Genre("Bukkake", "bukkake"),
    Genre("Chicas monstruo", "chicas-monstruo"),
    Genre("Chikan", "chikan"),
    Genre("Colegialas", "colegialas"),
    Genre("Comics porno", "comics-porno"),
    Genre("Dark Skin", "dark-skin"),
    Genre("Demonios", "demonios"),
    Genre("Ecchi", "ecchi"),
    Genre("Embarazadas", "embarazadas"),
    Genre("Enfermeras", "enfermeras"),
    Genre("Eroges", "eroges"),
    Genre("Fantasía", "fantasia"),
    Genre("Futanari", "futanari"),
    Genre("Gangbang", "gangbang"),
    Genre("Gemelas", "gemelas"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Gore", "gore"),
    Genre("Handjob", "handjob"),
    Genre("Harem", "harem"),
    Genre("Hipnosis", "hipnosis"),
    Genre("Incesto", "incesto"),
    Genre("Loli", "loli"),
    Genre("Maids", "maids"),
    Genre("Masturbación", "masturbacion"),
    Genre("Milf", "milf"),
    Genre("Mind Break", "mind-break"),
    Genre("My Hero Academia", "my-hero-academia"),
    Genre("Naruto", "naruto"),
    Genre("Netorare", "netorare"),
    Genre("Paizuri", "paizuri"),
    Genre("Pokemon", "pokemon"),
    Genre("Profesora", "profesora"),
    Genre("Prostitución", "prostitucion"),
    Genre("Romance", "romance"),
    Genre("Straight Shota", "straight-shota"),
    Genre("Tentáculos", "tentaculos"),
    Genre("Virgen", "virgen"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)
