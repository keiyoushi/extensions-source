package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class Manhwa18CcFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Manhwa18CcEN(),
        Manhwa18CcKO(),
        Manhwa18CcALL(),
    )
}

/** No language filtering — shows everything. */
class Manhwa18CcALL : Manhwa18Cc("all")

/** English variant — excludes Korean raws (titles ending with "Raw"). */
class Manhwa18CcEN : Manhwa18Cc("en") {
    override fun isMangaForLang(title: String) = !title.endsWith("Raw", ignoreCase = true)
}

/** Korean variant — includes only raws (titles ending with "Raw"). */
class Manhwa18CcKO : Manhwa18Cc("ko") {
    override fun isMangaForLang(title: String) = title.endsWith("Raw", ignoreCase = true)
}
