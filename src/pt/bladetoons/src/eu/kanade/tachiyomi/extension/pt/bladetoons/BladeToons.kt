package eu.kanade.tachiyomi.extension.pt.bladetoons

import eu.kanade.tachiyomi.multisrc.mangotheme.MangoTheme
import keiyoushi.annotation.Source

@Source
abstract class BladeToons : MangoTheme() {

    override val cdnUrl = "https://cdn.bladetoons.com"

    override val encryptionKey = "abmPisXlFjOLVTnYhbYQTpkWJtOGKwVttzLqstfjRBNVaEtQYG"

    override val webMangaPathSegment = "obra"

    override val webUrlSalt = "mango-secret-salt-2024"

    override fun buildTimedWebMangaReference(mangaId: String, hash: String): String = "$mangaId$hash${mangaId.firstOrNull()?.toString().orEmpty()}"

    override fun getStatusFilterOptions() = BladeToonsFilters.statusOptions

    override fun getFormatFilterOptions() = BladeToonsFilters.formatOptions

    override fun getTagFilterOptions() = BladeToonsFilters.tagOptions
}
