package eu.kanade.tachiyomi.extension.vi.minotruyen

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MinoTruyenFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MinoTruyen("MinoTruyen Manga", "manga"),
        MinoTruyen("MinoTruyen Comics", "comics"),
        MinoTruyen("MinoTruyen Hentai", "hentai"),
    )
}
