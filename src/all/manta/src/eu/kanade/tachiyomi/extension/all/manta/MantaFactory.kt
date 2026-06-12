package eu.kanade.tachiyomi.extension.all.manta

import eu.kanade.tachiyomi.source.SourceFactory

class MantaFactory : SourceFactory {
    override fun createSources() = listOf(
        object : MantaComics("en") {
            override val id: Long = 8753096034341798862L
        },
        MantaComics("es"),
    )
}
