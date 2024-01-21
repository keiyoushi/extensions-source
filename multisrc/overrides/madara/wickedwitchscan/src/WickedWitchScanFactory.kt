package eu.kanade.tachiyomi.extension.pt.wickedwitchscan

import eu.kanade.tachiyomi.source.SourceFactory

class WickedWitchScanFactory : SourceFactory {
    override fun createSources() = listOf(
        WickedWitchScan(),
        WickedWitchScanNew(),
    )
}
