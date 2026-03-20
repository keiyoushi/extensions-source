package eu.kanade.tachiyomi.extension.pt.imperiodabritannia

import eu.kanade.tachiyomi.multisrc.mangotheme.MangoTheme

class ImperioDaBritannia : MangoTheme() {

    override val name = "Sagrado Imp\u00e9rio da Britannia"

    override val baseUrl = "https://imperiodabritannia.net"

    override val lang = "pt-BR"

    override val cdnUrl = "https://cdn.imperiodabritannia.net"

    override val encryptionKey = "mangotoons_encryption_key_2025"

    override val webMangaPathSegment = "manga"

    override fun getStatusFilterOptions() = ImperioDaBritanniaFilters.statusOptions

    override fun getFormatFilterOptions() = ImperioDaBritanniaFilters.formatOptions

    override fun getTagFilterOptions() = ImperioDaBritanniaFilters.tagOptions
}
