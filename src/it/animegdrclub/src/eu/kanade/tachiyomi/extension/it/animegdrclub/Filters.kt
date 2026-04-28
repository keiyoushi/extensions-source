package eu.kanade.tachiyomi.extension.it.animegdrclub

import eu.kanade.tachiyomi.source.model.Filter

internal class SelezType(options: List<String>) : Filter.Select<String>("Scegli quale usare", options.toTypedArray(), 1)
internal class GenreSelez(genres: List<String>) : Filter.Select<String>("Genere", genres.toTypedArray(), 0)
internal class Status(name: String, val id: String = name) : Filter.CheckBox(name, true)
internal class StatusList(statuses: List<Status>) : Filter.Group<Status>("Stato", statuses)

internal fun getStatusList() = listOf(
    Status("In corso", "progettiincorso"),
    Status("Finito", "progetticonclusi-progettioneshot"),
    Status("Interrotto", "progettiinterrotti"),
)

internal fun getGenreList() = listOf(
    "Avventura", "Azione", "Comico", "Commedia", "Drammatico", "Ecchi",
    "Fantascienza", "Fantasy", "Guerra", "Harem", "Horror", "Isekai",
    "Mecha", "Mistero", "Musica", "Psicologico", "Scolastico", "Sentimentale",
    "Slice of Life", "Sovrannaturale", "Sperimentale", "Storico", "Thriller",
)
