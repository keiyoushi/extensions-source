package eu.kanade.tachiyomi.extension.zh.baozimhorg

import eu.kanade.tachiyomi.source.SourceFactory

// This is not used because ideally the extension language should be updated to "Multi" (all).
// Chinese users don't receive status updates from Discord, so I'll keep the package name unchanged for now.
class GoDaFactory : SourceFactory {
    override fun createSources() = listOf(
        GoDaManhua(),
        BaozimhOrg("Goda", "https://manhuascans.org", "en"),
    )
}
