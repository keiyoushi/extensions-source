package eu.kanade.tachiyomi.extension.all.mangaup

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaUpFactory : SourceFactory {
    /**
     * They only have English for now, but the website does have a
     * language selector and the API also supports that.
     *
     * Probably it's something they will add in the future, so better
     * to already make the extension a multilang to avoid users having
     * to migrate to an All extension after.
     */
    override fun createSources(): List<Source> = listOf(MangaUp("en"))
}
