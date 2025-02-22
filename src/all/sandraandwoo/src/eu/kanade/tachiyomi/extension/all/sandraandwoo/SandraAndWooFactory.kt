package eu.kanade.tachiyomi.extension.all.sandraandwoo
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.extension.all.sandraandwoo.translations.SandraAndWooDE
import eu.kanade.tachiyomi.extension.all.sandraandwoo.translations.SandraAndWooEN
import eu.kanade.tachiyomi.source.SourceFactory

class SandraAndWooFactory : SourceFactory {
    override fun createSources() = listOf(
        SandraAndWooDE(),
        SandraAndWooEN(),
    )
}
