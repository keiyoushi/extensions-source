package eu.kanade.tachiyomi.extension.en.dynasty

import eu.kanade.tachiyomi.source.SourceFactory

class DynastyFactory : SourceFactory {
    override fun createSources() = listOf(
        Dynasty(),
        DynastyLegacy("Dynasty-Anthologies (Deprecated)", 738706855355689486),
        DynastyLegacy("Dynasty-Chapters (Deprecated)", 4399127807078496448),
        DynastyLegacy("Dynasty-Doujins (Deprecated)", 6243685045159195166),
        DynastyLegacy("Dynasty-Issues (Deprecated)", 2548005429321146934),
    )
}
