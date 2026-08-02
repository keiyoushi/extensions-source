package eu.kanade.tachiyomi.extension.en.mangabat

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source

@Source
abstract class Mangabat : MangaBox() {
    override fun getMangaUrl(manga: SManga): String {
        if (manga.url.contains("mangabat.com/")) {
            throw Exception(MIGRATE_MESSAGE)
        }
        return super.getMangaUrl(manga)
    }
    companion object {
        private const val MIGRATE_MESSAGE = "Migrate this entry from \"Mangabat\" to \"Mangabat\" to continue reading"
    }
}
