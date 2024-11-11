package eu.kanade.tachiyomi.extension.all.pururin

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class PururinFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        PururinAll(),
        PururinEN(),
        PururinJA(),
    )
}

class PururinAll : Pururin()
class PururinEN : Pururin(
    "en",
    Pair("13010", "english"),
    "/tags/language/13010/english",
)
class PururinJA : Pururin(
    "ja",
    Pair("13011", "japanese"),
    "/tags/language/13011/japanese",
)
