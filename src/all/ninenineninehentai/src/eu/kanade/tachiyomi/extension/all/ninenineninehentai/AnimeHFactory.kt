package eu.kanade.tachiyomi.extension.all.ninenineninehentai

import eu.kanade.tachiyomi.source.SourceFactory

class AnimeHFactory : SourceFactory {
    override fun createSources() = listOf(
        AnimeHAll(),
        AnimeHEn(),
        AnimeHJa(),
        AnimeHZh(),
        AnimeHEs(),
    )
}

class AnimeHAll : AnimeH("all") { override val id = 5098173700376022513 }
class AnimeHEn : AnimeH("en") { override val id = 4370122548313941497 }
class AnimeHJa : AnimeH("ja", "jp") { override val id = 8948948503520127713 }
class AnimeHZh : AnimeH("zh", "cn") { override val id = 3874510362699054213 }
class AnimeHEs : AnimeH("es") { override val id = 2790053117909987291 }
