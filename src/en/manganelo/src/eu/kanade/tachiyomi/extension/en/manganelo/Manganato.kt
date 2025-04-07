package eu.kanade.tachiyomi.extension.en.manganelo

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request

class Manganato : MangaBox(
    "Manganato",
    arrayOf(
        "www.natomanga.com",
        "www.nelomanga.com",
        "www.manganato.gg",
    ),
    "en",
) {

    override val id: Long = 1024627298672457456

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (LEGACY_DOMAINS.any { manga.url.startsWith(it) }) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.mangaDetailsRequest(manga)
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
