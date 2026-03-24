package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.multisrc.mangotheme.MangoTheme

class MangoToons : MangoTheme() {

    override val name = "Mango Toons"

    override val baseUrl = "https://mangotoons.com"

    override val lang = "pt-BR"

    override val encryptionKey = "abmPisXlFjOLVTnYhbYQTpkWJtOGKwVttzLqstfjRBNVaEtQYG"

    override val cdnUrl = "https://cdn.mangotoons.com"

    override val webUrlSalt = "mango-secret-salt-2024"

    override val requiresLogin = true

    override fun getStatusFilterOptions() = MangoToonsFilters.statusOptions

    override fun getFormatFilterOptions() = MangoToonsFilters.formatOptions

    override fun getTagFilterOptions() = MangoToonsFilters.tagOptions
}
