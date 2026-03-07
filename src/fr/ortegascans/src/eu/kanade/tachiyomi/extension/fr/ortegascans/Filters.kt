package eu.kanade.tachiyomi.extension.fr.ortegascans

import eu.kanade.tachiyomi.source.model.Filter

open class ValueCheckBox(name: String, val value: String) : Filter.CheckBox(name)

abstract class CheckboxGroupFilter<T : ValueCheckBox>(name: String, state: List<T>) : Filter.Group<T>(name, state) {
    val values: String
        get() = state.filter { it.state }.joinToString(",") { it.value }
}

open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value get() = vals[state].second
}

abstract class IntTextFilter(name: String, val default: String) : Filter.Text(name, default) {
    val value: String
        get() = state.ifEmpty { default }.also {
            if (it.toIntOrNull() == null) {
                throw Exception("Le champ '$name' doit être un nombre entier")
            }
        }
}

class SortFilter :
    UriPartFilter(
        "Trier par",
        arrayOf(
            Pair("Popularité", "popular"),
            Pair("Ordre alphabétique", "alpha"),
            Pair("Plus récent", "recent"),
        ),
    )

class StatusCheckBox(name: String, value: String) : ValueCheckBox(name, value)

class StatusFilter :
    CheckboxGroupFilter<StatusCheckBox>(
        "Statut",
        listOf(
            StatusCheckBox("En cours", "en cours"),
            StatusCheckBox("Terminé", "terminé"),
            StatusCheckBox("En pause", "en pause"),
            StatusCheckBox("Annulé", "annulé"),
        ),
    )

class TagCheckBox(name: String) : ValueCheckBox(name, name)

class TagFilter :
    CheckboxGroupFilter<TagCheckBox>(
        "Tags",
        listOf(
            "Action", "Aventure", "Comédie", "Drame", "Esclave", "Fantaisie", "Fétichisme",
            "Hardcore", "Harem", "Humiliation", "Hypnose", "Isekai", "Mature", "MILF", "Partenaire",
            "Pouvoir", "Revanche", "Romance", "Seinen", "Sport", "Surnaturel", "Système", "Tranche de Vie", "Vie Scolaire",
        ).map(::TagCheckBox),
    )

class MinChaptersFilter : IntTextFilter("Nombre minimum de chapitres", "0")

class MaxChaptersFilter : IntTextFilter("Nombre maximum de chapitres", "9999")
