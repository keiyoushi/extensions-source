package eu.kanade.tachiyomi.extension.all.coronaex

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter :
    SelectFilter(
        "Genres",
        arrayOf(
            Pair("少女マンガ", "41114554925057"),
            Pair("青年マンガ", "41114574733314"),
            Pair("少年マンガ", "41114587152387"),
            Pair("Celicaコミックス", "233327015772195"),
            Pair("女性マンガ", "234901641199652"),
            Pair("ファンタジー", "235983689498667"),
            Pair("異世界・転生", "235983706292268"),
            Pair("コロナ・コミックス", "235983585656869"),
            Pair("その他マンガ", "41114622885893"),
            Pair("恋愛", "235983673786410"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
