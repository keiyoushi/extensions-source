package eu.kanade.tachiyomi.extension.ja.yanmaga

import eu.kanade.tachiyomi.source.SourceFactory

class YanmagaFactory : SourceFactory {
    override fun createSources() = listOf(
        YanmagaComics(),
        YanmagaGravures(),
    )
}
