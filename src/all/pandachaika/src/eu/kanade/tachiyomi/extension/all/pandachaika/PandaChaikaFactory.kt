package eu.kanade.tachiyomi.extension.all.pandachaika

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PandaChaikaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        PandaChaika(),
        PandaChaika("en", "english"),
        PandaChaika("zh", "chinese"),
        PandaChaika("ko", "korean"),
        PandaChaika("es", "spanish"),
        PandaChaika("ru", "russian"),
        PandaChaika("pt", "portuguese"),
        PandaChaika("fr", "french"),
        PandaChaika("th", "thai"),
        PandaChaika("vi", "vietnamese"),
        PandaChaika("ja", "japanese"),
        PandaChaika("id", "indonesian"),
        PandaChaika("ar", "arabic"),
        PandaChaika("uk", "ukrainian"),
        PandaChaika("tr", "turkish"),
        PandaChaika("cs", "czech"),
        PandaChaika("tl", "tagalog"),
        PandaChaika("fi", "finnish"),
        PandaChaika("jv", "javanese"),
        PandaChaika("el", "greek"),
    )
}
