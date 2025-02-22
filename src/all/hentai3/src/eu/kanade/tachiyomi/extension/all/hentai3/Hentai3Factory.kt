package eu.kanade.tachiyomi.extension.all.hentai3

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class Hentai3Factory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Hentai3("all", ""),
        Hentai3("en", "english"),
        Hentai3("ja", "japanese"),
        Hentai3("ko", "korean"),
        Hentai3("zh", "chinese"),
        Hentai3("mo", "mongolian"),
        Hentai3("es", "spanish"),
        Hentai3("pt", "Portuguese"),
        Hentai3("id", "indonesian"),
        Hentai3("jv", "javanese"),
        Hentai3("tl", "tagalog"),
        Hentai3("vi", "vietnamese"),
        Hentai3("th", "thai"),
        Hentai3("my", "burmese"),
        Hentai3("tr", "turkish"),
        Hentai3("ru", "russian"),
        Hentai3("uk", "ukrainian"),
        Hentai3("po", "polish"),
        Hentai3("fi", "finnish"),
        Hentai3("de", "german"),
        Hentai3("it", "italian"),
        Hentai3("fr", "french"),
        Hentai3("nl", "dutch"),
        Hentai3("cs", "czech"),
        Hentai3("hu", "hungarian"),
        Hentai3("bg", "bulgarian"),
        Hentai3("is", "icelandic"),
        Hentai3("la", "latin"),
        Hentai3("ar", "arabic"),
    )
}
