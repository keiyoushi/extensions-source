package eu.kanade.tachiyomi.extension.id.storyaboutus

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("Semua", "Berjalan", "Tamat", "Akhir Season", "Hiatus"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "ongoing"
        2 -> "end"
        3 -> "end-season"
        4 -> "hiatus"
        else -> ""
    }
}
