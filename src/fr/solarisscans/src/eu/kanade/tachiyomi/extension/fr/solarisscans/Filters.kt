package eu.kanade.tachiyomi.extension.fr.solarisscans

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}

class SortFilter :
    UriPartFilter(
        "Tri",
        arrayOf(
            "Popularité" to "popular",
            "Ajout récent" to "recent",
            "Plus de chapitres" to "chapters",
            "Ordre alphabétique" to "alpha",
        ),
    )

class CheckBoxVal(name: String) : Filter.CheckBox(name)

class StatusFilter :
    Filter.Group<CheckBoxVal>(
        "Statut",
        listOf(
            CheckBoxVal("En cours"),
            CheckBoxVal("Terminé"),
            CheckBoxVal("En pause"),
            CheckBoxVal("Abandonné"),
        ),
    ) {
    fun toUriParts(): List<String> {
        val values = listOf("en-cours", "termine", "pause", "abandonne")
        return state.mapIndexedNotNull { index, checkBox ->
            values.getOrNull(index)?.takeIf { checkBox.state }
        }
    }
}

class TypeFilter :
    Filter.Group<CheckBoxVal>(
        "Type d’œuvre",
        listOf(
            CheckBoxVal("Manhwa"),
            CheckBoxVal("Manhua"),
            CheckBoxVal("Manga"),
        ),
    ) {
    fun toUriParts(): List<String> {
        val values = listOf("kr", "cn", "jp")
        return state.mapIndexedNotNull { index, checkBox ->
            values.getOrNull(index)?.takeIf { checkBox.state }
        }
    }
}
