package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.source.SourceFactory

class GoDaFactory : SourceFactory {
    override fun createSources() = listOf(
        GoDaManhua(),
        BaozimhOrg("Goda", "https://manhuascans.org", "en"),
    )
}
