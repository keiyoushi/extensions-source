package eu.kanade.tachiyomi.extension.all.solarmtl

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
