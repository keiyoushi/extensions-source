package eu.kanade.tachiyomi.extension.en.manganelo

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source

@Source
abstract class Manganato : MangaBox() {

    override fun getMangaUrl(manga: SManga): String {
        if (LEGACY_DOMAINS.any { manga.url.startsWith(it) }) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.getMangaUrl(manga)
    }
    companion object {
        private val LEGACY_DOMAINS = arrayOf(
            "https://chapmanganato.to/",
            "https://manganato.com/",
            "https://readmanganato.com/",
        )
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Manganato\" to \"Manganato\" to continue reading"
    }
}
