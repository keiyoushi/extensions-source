package eu.kanade.tachiyomi.extension.en.vizshonenjump

import eu.kanade.tachiyomi.source.SourceFactory

class VizFactory : SourceFactory {
    override fun createSources() = listOf(
        Viz("VIZ Shonen Jump", "shonenjump"),
        Viz("VIZ Manga", "vizmanga"),
    )
}
