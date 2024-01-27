package eu.kanade.tachiyomi.extension.all.lanraragi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LANraragiFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val firstLrr = LANraragi("1")
        val lrrCount = firstLrr.preferences.getString(LANraragi.EXTRA_SOURCES_COUNT_KEY, LANraragi.EXTRA_SOURCES_COUNT_DEFAULT)!!.toInt()

        return buildList(lrrCount) {
            add(firstLrr)
            for (i in 2..lrrCount) {
                add(LANraragi("$i"))
            }
        }
    }
}
