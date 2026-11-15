package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit
import keiyoushi.annotation.Source

@Source
abstract class MaidScan : GreenShit() {
    override val apiUrl = "https://api.verdinha.wtf"
    override val cdnUrl = "https://cdn.verdinha.wtf"
    override val cdnApiUrl = "https://api.verdinha.wtf/cdn"
    override val scanId = "3"

    override val defaultGenreId = "4"

    override val formatsList = arrayOf(
        Pair("Todos", ""),
        Pair("Manhwa", "16"),
        Pair("Novel", "17"),
    )

    override val statusList = arrayOf(
        Pair("Todos", ""),
        Pair("Cancelado", "13"),
        Pair("Completo", "12"),
        Pair("Em Andamento", "10"),
        Pair("Hiato", "11"),
    )

    override val tagsList = emptyArray<Pair<String, String>>()
}
