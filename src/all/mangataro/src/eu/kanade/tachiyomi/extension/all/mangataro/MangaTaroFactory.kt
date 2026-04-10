package eu.kanade.tachiyomi.extension.all.mangataro

import eu.kanade.tachiyomi.multisrc.mangataro.MangaTaro
import eu.kanade.tachiyomi.multisrc.mangataro.MangaTaroGroup
import eu.kanade.tachiyomi.source.SourceFactory

class MangaTaroFactory : SourceFactory {
    override fun createSources() = listOf(
        MangaTaroSource("en"),
        MangaTaroGroupSource("pt-BR", groups = listOf(9)),
    )
}

class MangaTaroSource(lang: String) : MangaTaro("MangaTaro", "https://mangataro.org", lang)

class MangaTaroGroupSource(lang: String, groups: List<Long>) : MangaTaroGroup("MangaTaro", "https://mangataro.org", lang, groups)
