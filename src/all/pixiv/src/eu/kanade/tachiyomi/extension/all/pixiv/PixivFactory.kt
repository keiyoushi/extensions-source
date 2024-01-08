package eu.kanade.tachiyomi.extension.all.pixiv

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PixivFactory : SourceFactory {
    override fun createSources(): List<Source> =
        listOf("ja", "en", "ko", "zh").map { lang -> Pixiv(lang) }
}
