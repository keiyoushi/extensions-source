package eu.kanade.tachiyomi.extension.all.solarmtl
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.LanguageImpl
import eu.kanade.tachiyomi.source.SourceFactory

@RequiresApi(Build.VERSION_CODES.O)
class SolarmtlFactory : SourceFactory {
    override fun createSources() = languageList.map(::Solarmtl)
}

private val languageList = listOf(
    LanguageImpl("en"),
    LanguageImpl("fr"),
    LanguageImpl("pt-BR", "pt"),
)
