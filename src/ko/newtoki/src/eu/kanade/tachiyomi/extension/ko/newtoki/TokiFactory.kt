package eu.kanade.tachiyomi.extension.ko.newtoki

import eu.kanade.tachiyomi.source.SourceFactory

class TokiFactory : SourceFactory {
    override fun createSources() = listOf(ManaToki, NewTokiWebtoon)
}
