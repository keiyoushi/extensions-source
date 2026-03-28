package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LunarAnimeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LunarAnime("all"),
        LunarAnime("en"),
        LunarAnime("ar"),
        LunarAnime("bg"),
        LunarAnime("bn"),
        LunarAnime("da"),
        LunarAnime("de"),
        LunarAnime("es"),
        LunarAnime("es-419"),
        LunarAnime("fa"),
        LunarAnime("fi"),
        LunarAnime("fr"),
        LunarAnime("he"),
        LunarAnime("hi"),
        LunarAnime("id"),
        LunarAnime("it"),
        LunarAnime("ja"),
        LunarAnime("ko"),
        LunarAnime("ms"),
        LunarAnime("nl"),
        LunarAnime("no"),
        LunarAnime("pl"),
        LunarAnime("pt"),
        LunarAnime("pt-BR", "pt-br"),
        LunarAnime("ru"),
        LunarAnime("sv"),
        LunarAnime("th"),
        LunarAnime("tl"),
        LunarAnime("tr"),
        LunarAnime("ur"),
        LunarAnime("vi"),
        LunarAnime("zh"),
    )
}
