package eu.kanade.tachiyomi.extension.all.akuma

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class AkumaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Akuma("all", "all"),
        Akuma("en", "english"),
        Akuma("id", "indonesian"),
        Akuma("jv", "javanese"),
        Akuma("ca", "catalan"),
        Akuma("ceb", "cebuano"),
        Akuma("cs", "czech"),
        Akuma("da", "danish"),
        Akuma("de", "german"),
        Akuma("et", "estonian"),
        Akuma("es", "spanish"),
        Akuma("eo", "esperanto"),
        Akuma("fr", "french"),
        Akuma("it", "italian"),
        Akuma("hi", "hindi"),
        Akuma("hu", "hungarian"),
        Akuma("nl", "dutch"),
        Akuma("pl", "polish"),
        Akuma("pt", "portuguese"),
        Akuma("vi", "vietnamese"),
        Akuma("tr", "turkish"),
        Akuma("ru", "russian"),
        Akuma("uk", "ukrainian"),
        Akuma("ar", "arabic"),
        Akuma("ko", "korean"),
        Akuma("zh", "chinese"),
        Akuma("ja", "japanese"),
    )
}
