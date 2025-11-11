package eu.kanade.tachiyomi.extension.all.mayotune

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MayoTuneFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MayoTune("en", ""),
        MayoTune("ja", "raw"),
    )
}
