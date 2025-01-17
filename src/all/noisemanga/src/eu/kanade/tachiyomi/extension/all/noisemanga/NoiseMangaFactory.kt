package eu.kanade.tachiyomi.extension.all.noisemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NoiseMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NoiseMangaEnglish(),
        NoiseMangaPortuguese(),
    )
}

class NoiseMangaEnglish : NoiseManga("en")

class NoiseMangaPortuguese : NoiseManga("pt-BR") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 8279458690164834090
}
