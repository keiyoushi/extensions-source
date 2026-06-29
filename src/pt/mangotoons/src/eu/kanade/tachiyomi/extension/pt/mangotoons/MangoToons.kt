package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.multisrc.mangotheme.MangoTheme
import keiyoushi.annotation.Source

@Source
abstract class MangoToons : MangoTheme() {

    override val apiUrl = "https://api.mangotoons.com/api"

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", "-")
        .set("sec-fetch-mode", "none")

    override val encryptionKey = "abmPisXlFjOLVTnYhbYQTpkWJtOGKwVttzLqstfjRBNVaEtQYG"

    override val cdnUrl = "https://cdn.mangotoons.com"

    override val webUrlSalt = "mango-secret-salt-2024"

    override val requiresLogin = true

    override fun getStatusFilterOptions() = MangoToonsFilters.statusOptions

    override fun getFormatFilterOptions() = MangoToonsFilters.formatOptions

    override fun getTagFilterOptions() = MangoToonsFilters.tagOptions
}
