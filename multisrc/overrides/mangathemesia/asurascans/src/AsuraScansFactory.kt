package eu.kanade.tachiyomi.extension.all.asurascans

import eu.kanade.tachiyomi.source.SourceFactory

class AsuraScansFactory : SourceFactory {
    override fun createSources() = listOf(
        AsuraScansEn(),
        AsuraScansTr(),
    )
}
