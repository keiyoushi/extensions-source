package eu.kanade.tachiyomi.extension.all.netcomics

import eu.kanade.tachiyomi.source.SourceFactory

class NetcomicsFactory : SourceFactory {
    override fun createSources() = listOf(
        Netcomics("en", "EN"),
        Netcomics("ja", "JA"),
        Netcomics("zh", "CN"),
        Netcomics("ko", "KO"),
        Netcomics("es", "ES"),
        Netcomics("fr", "FR"),
        Netcomics("de", "DE"),
        Netcomics("id", "ID"),
        Netcomics("vi", "VI"),
        Netcomics("th", "TH"),
    )
}
