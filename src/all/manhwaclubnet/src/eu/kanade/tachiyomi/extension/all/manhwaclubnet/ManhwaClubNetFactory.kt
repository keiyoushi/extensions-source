package eu.kanade.tachiyomi.extension.all.manhwaclubnet

import eu.kanade.tachiyomi.source.SourceFactory

class ManhwaClubNetFactory : SourceFactory {
    // Font for icon: Cooper BT Std Black Headline
    override fun createSources() = listOf(
        ManhwaClubNet("en"),
        ManhwaClubNet("ko"),
    )
}
