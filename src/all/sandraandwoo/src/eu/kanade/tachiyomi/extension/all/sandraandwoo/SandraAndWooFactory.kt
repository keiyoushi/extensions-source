package eu.kanade.tachiyomi.extension.all.sandraandwoo

import eu.kanade.tachiyomi.extension.all.sandraandwoo.translations.SandraAndWooDE
import eu.kanade.tachiyomi.extension.all.sandraandwoo.translations.SandraAndWooEN
import eu.kanade.tachiyomi.source.SourceFactory

class SandraAndWooFactory : SourceFactory {
    override fun createSources() = listOf(
        SandraAndWooDE(),
        SandraAndWooEN(),
    )
}
