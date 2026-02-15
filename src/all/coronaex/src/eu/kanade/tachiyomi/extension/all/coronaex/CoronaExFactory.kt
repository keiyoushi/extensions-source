package eu.kanade.tachiyomi.extension.all.coronaex

import eu.kanade.tachiyomi.source.SourceFactory

class CoronaExFactory : SourceFactory {
    override fun createSources() = listOf(
        CoronaEx("ja", "to-corona-ex.com"),
        CoronaEx("en", "en.to-corona-ex.com"),
    )
}
