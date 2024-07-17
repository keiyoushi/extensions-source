package eu.kanade.tachiyomi.extension.all.galaxy

import eu.kanade.tachiyomi.source.SourceFactory

class GalaxyFactory : SourceFactory {

    class GalaxyWebtoon : Galaxy("Galaxy Webtoon", "https://galaxyaction.net", "en") {
        override val id = 2602904659965278831
    }

    class GalaxyManga : Galaxy("Galaxy Manga", "https://ayoub-zrr.xyz", "ar") {
        override val id = 2729515745226258240
    }

    override fun createSources() = listOf(
        GalaxyWebtoon(),
        GalaxyManga(),
    )
}
