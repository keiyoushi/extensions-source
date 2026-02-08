package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.multisrc.greenshit.GreenShit

class MaidScan : GreenShit() {
    override val apiUrl = "https://api.verdinha.wtf"
    override val cdnUrl = "https://cdn.verdinha.wtf"
    override val baseUrl = "https://empreguetes.xyz"
    override val cdnApiUrl = "https://api.verdinha.wtf/cdn"
    override val lang = "pt-BR"
    override val name = "Maid Scan"
    override val scanId = "3"

    override val defaultGenreId = "4"

    override fun getGeneroFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Novel", "6"),
        Pair("Shoujo / Romances", "4"),
        Pair("Yaoi", "7"),
    )

    override fun getFormatoFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Manhwa", "16"),
        Pair("Novel", "17"),
    )

    override fun getStatusFilterOptions(): Array<Pair<String, String>> = arrayOf(
        Pair("Todos", ""),
        Pair("Cancelado", "13"),
        Pair("Completo", "12"),
        Pair("Em Andamento", "10"),
        Pair("Hiato", "11"),
    )

    override fun getTagsFilterOptions() = emptyList<TagCheckBox>()
}
