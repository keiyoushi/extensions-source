package eu.kanade.tachiyomi.extension.all.yskcomics

import eu.kanade.tachiyomi.source.SourceFactory

class YSKComicsFactory : SourceFactory {
    override fun createSources() = listOf(
        YSKComics("ar"),
        YSKComics("en"),
    )
}
