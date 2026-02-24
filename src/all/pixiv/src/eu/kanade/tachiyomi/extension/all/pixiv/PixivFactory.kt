package eu.kanade.tachiyomi.extension.all.pixiv

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PixivFactory : SourceFactory {
    override fun createSources(): List<Source> = KNOWN_LOCALES.map { lang -> Pixiv(lang) }
}
