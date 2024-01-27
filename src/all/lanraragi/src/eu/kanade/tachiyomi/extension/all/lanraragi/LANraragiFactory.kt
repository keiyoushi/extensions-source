package eu.kanade.tachiyomi.extension.all.lanraragi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class LANraragiFactory : SourceFactory {
    override fun createSources(): List<Source> {
        val firstLrr = LANraragi("1")
        val lrrCount = firstLrr.preferences.getString(LANraragi.EXTRA_SOURCES_COUNT_KEY, LANraragi.EXTRA_SOURCES_COUNT_DEFAULT)!!.toInt()

        return mutableListOf(firstLrr).apply {
            (0 until lrrCount).map { add(LANraragi("${it + 2}")) }
        }
    }
}
