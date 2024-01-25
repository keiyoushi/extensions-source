package eu.kanade.tachiyomi.extension.all.hitomi

import eu.kanade.tachiyomi.source.SourceFactory

class HitomiFactory : SourceFactory {
    override fun createSources() = listOf(
        Hitomi("all", "all"),
        Hitomi("en", "english"),
        Hitomi("id", "indonesian"),
        Hitomi("jv", "javanese"),
        Hitomi("ca", "catalan"),
        Hitomi("ceb", "cebuano"),
        Hitomi("cs", "czech"),
        Hitomi("da", "danish"),
        Hitomi("de", "german"),
        Hitomi("et", "estonian"),
        Hitomi("es", "spanish"),
        Hitomi("eo", "esperanto"),
        Hitomi("fr", "french"),
        Hitomi("it", "italian"),
        Hitomi("hi", "hindi"),
        Hitomi("hu", "hungarian"),
        Hitomi("pl", "polish"),
        Hitomi("pt", "portuguese"),
        Hitomi("vi", "vietnamese"),
        Hitomi("tr", "turkish"),
        Hitomi("ru", "russian"),
        Hitomi("uk", "ukrainian"),
        Hitomi("ar", "arabic"),
        Hitomi("ko", "korean"),
        Hitomi("zh", "chinese"),
        Hitomi("ja", "japanese"),
    )
}
