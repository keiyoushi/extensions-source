package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.source.model.Filter

internal class TextField(name: String, val key: String) : Filter.Text(name)

internal class SortBy :
    UriPartFilter(
        "Ordina per",
        arrayOf(
            Pair("Rilevanza", ""),
            Pair("Più letti", "most_read"),
            Pair("Meno letti", "less_read"),
            Pair("Più recenti", "newest"),
            Pair("Meno recenti", "oldest"),
            Pair("A-Z", "a-z"),
            Pair("Z-A", "z-a"),
        ),
    )

internal class Genre(name: String, val id: String = name) : Filter.CheckBox(name)
internal class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Generi", genres)

internal class MType(name: String, val id: String = name) : Filter.CheckBox(name)
internal class MTypeList(types: List<MType>) : Filter.Group<MType>("Tipologia", types)

internal class Status(name: String, val id: String = name) : Filter.CheckBox(name)
internal class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

internal open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

internal val GENRES = listOf(
    Genre("Adulti", "adulti"),
    Genre("Arti Marziali", "arti-marziali"),
    Genre("Avventura", "avventura"),
    Genre("Azione", "azione"),
    Genre("Commedia", "commedia"),
    Genre("Doujinshi", "doujinshi"),
    Genre("Drammatico", "drammatico"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Harem", "harem"),
    Genre("Hentai", "hentai"),
    Genre("Horror", "horror"),
    Genre("Josei", "josei"),
    Genre("Lolicon", "lolicon"),
    Genre("Maturo", "maturo"),
    Genre("Mecha", "mecha"),
    Genre("Mistero", "mistero"),
    Genre("Psicologico", "psicologico"),
    Genre("Romantico", "romantico"),
    Genre("Sci-fi", "sci-fi"),
    Genre("Scolastico", "scolastico"),
    Genre("Seinen", "seinen"),
    Genre("Shotacon", "shotacon"),
    Genre("Shoujo", "shoujo"),
    Genre("Shoujo Ai", "shoujo-ai"),
    Genre("Shounen", "shounen"),
    Genre("Shounen Ai", "shounen-ai"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Soprannaturale", "soprannaturale"),
    Genre("Sport", "sport"),
    Genre("Storico", "storico"),
    Genre("Tragico", "tragico"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
)

internal val MTYPES = listOf(
    MType("Manga", "manga"),
    MType("Manhua", "manhua"),
    MType("Manhwa", "manhwa"),
    MType("Oneshot", "oneshot"),
    MType("Thai", "thai"),
    MType("Vietnamita", "vietnamese"),
)

internal val STATUSES = listOf(
    Status("In corso", "ongoing"),
    Status("Finito", "completed"),
    Status("Droppato", "dropped"),
    Status("In pausa", "paused"),
    Status("Cancellato", "canceled"),
)
