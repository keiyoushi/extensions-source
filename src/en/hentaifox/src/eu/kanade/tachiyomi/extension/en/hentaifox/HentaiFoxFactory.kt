package eu.kanade.tachiyomi.extension.en.hentaifox

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class HentaiFoxFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HentaiFox("en", "english"),
        HentaiFox("ja", "japanese"),
        HentaiFox("zh", "chinese"),
        HentaiFox("ko", "korean"),
        HentaiFox("all", ""),
    )
}
