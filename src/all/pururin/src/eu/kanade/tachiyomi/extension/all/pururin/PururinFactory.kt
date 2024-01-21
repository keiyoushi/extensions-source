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
    "{\"id\":13010,\"name\":\"English [Language]\"}",
    "/tags/language/13010/english",
)
class PururinJA : Pururin(
    "ja",
    "{\"id\":13011,\"name\":\"Japanese [Language]\"}",
    "/tags/language/13011/japanese",
)
