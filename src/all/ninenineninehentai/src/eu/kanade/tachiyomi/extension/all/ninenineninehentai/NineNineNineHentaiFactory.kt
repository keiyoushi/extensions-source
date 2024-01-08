package eu.kanade.tachiyomi.extension.all.ninenineninehentai

import eu.kanade.tachiyomi.source.SourceFactory

class NineNineNineHentaiFactory : SourceFactory {
    override fun createSources() = listOf(
        NineNineNineHentai("all"),
        NineNineNineHentai("en"),
        NineNineNineHentai("ja", "jp"),
        NineNineNineHentai("zh", "cn"),
        NineNineNineHentai("es"),
    )
}
