package eu.kanade.tachiyomi.extension.all.asmhentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class ASMHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        AsmHentai("en", "english"),
        AsmHentai("ja", "japanese"),
        AsmHentai("zh", "chinese"),
        AsmHentai("all", ""),
    )
}
