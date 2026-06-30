package eu.kanade.tachiyomi.extension.en.mangabat

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.Request

@Source
abstract class Mangabat : MangaBox() {
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.contains("mangabat.com/")) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.mangaDetailsRequest(manga)
    }
    companion object {
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Mangabat\" to \"Mangabat\" to continue reading"
    }
}
