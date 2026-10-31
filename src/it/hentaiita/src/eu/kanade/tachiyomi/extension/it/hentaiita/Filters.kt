package eu.kanade.tachiyomi.extension.it.hentaiita

import eu.kanade.tachiyomi.source.model.Filter

enum class SortMode(val label: String, val apiOrderBy: String) {
    RANDOM("Casuale", "date"),
    BEST("Consigliati dal sito, poi recenti", "date"),
    DATE("Data di uscita", "date"),
    MODIFIED("Ultimo aggiornamento", "modified"),
    TITLE("Titolo", "title"),
    RELEVANCE("Pertinenza (con testo)", "relevance"),
}

class SortFilter(selection: Filter.Sort.Selection = Filter.Sort.Selection(0, false)) :
    Filter.Sort("Ordina per", SortMode.entries.map { it.label }.toTypedArray(), selection) {
    val mode: SortMode get() = SortMode.entries[state?.index ?: 0]
    val ascending: Boolean get() = state?.ascending ?: false
}

class Section(val label: String, val id: Int?)

class SectionFilter : Filter.Select<String>("Sezione", SECTIONS.map { it.label }.toTypedArray()) {
    val section: Section get() = SECTIONS[state]
}

class CategoryOption(name: String, val id: Int) : Filter.TriState(name)

class CategoryGroup(options: List<CategoryOption>) : Filter.Group<CategoryOption>("Categorie", options)

val SECTIONS = listOf(
    Section("Tutti i fumetti", null),
    Section("Manga hentai", 16),
    Section("Fumetti porno", 17),
    Section("Doujinshi", 47),
    Section("3D porn comics", 40),
)

const val BEST_CATEGORY_ID = 498

// Games, gameplay videos, subtitled videos and the "-mobile" duplicates of comics.
val NON_READING_CATEGORIES = mapOf(
    53 to "giochi-porno",
    442 to "gameplay",
    315 to "video-sub-ita",
    317 to "mobile",
)

// Only labels that explicitly mean children or preteens: series whose main
// characters are canonically children or middle-schoolers. Always excluded,
// both from the requests and again on the returned entries.
val BLOCKED_CATEGORIES = mapOf(
    145 to "sailor-moon",
    146 to "sailor-jupiter",
    204 to "sailor-mercury",
    368 to "sailor-venus",
    58 to "due-fantagenitori",
    409 to "boruto",
    332 to "doraemon",
    67 to "ben-ten",
    181 to "gravity-falls",
)

val BLOCKED_TEXT = Regex(
    """(?i)\b(loli\w*|shota\w*|shotta\w*|preteen\w*|pre-teen\w*|underage|minorenn\w*|minori|bambin[aeio]|child(ren)?|toddler\w*|kodomo|elementari)\b""",
)
