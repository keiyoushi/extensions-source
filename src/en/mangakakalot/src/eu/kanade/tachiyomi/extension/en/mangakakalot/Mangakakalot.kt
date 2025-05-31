package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request

class Mangakakalot : MangaBox(
    "Mangakakalot",
    arrayOf(
        "www.mangakakalot.gg",
        "www.mangakakalove.com",
    ),
    "en",
) {
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (LEGACY_DOMAINS.any { manga.url.startsWith(it) }) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.mangaDetailsRequest(manga)
    }
    companion object {
        private val LEGACY_DOMAINS = arrayOf(
            "https://chapmanganato.to/",
            "https://mangakakalot.com/",
            "https://manganelo.com/",
            "https://readmanganato.com/",
        )
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Mangakakalot\" to \"Mangakakalot\" to continue reading"
    }
}
