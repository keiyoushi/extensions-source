package eu.kanade.tachiyomi.extension.all.solarmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.source.SourceFactory

@RequiresApi(Build.VERSION_CODES.O)
class SolarmtlFactory : SourceFactory {
    override fun createSources() = languageList.map(::Solarmtl)
}

private val languageList = listOf(
    Language("ar", disableSourceSettings = true),
    Language("en"),
    Language("fr", disableTranslator = true, supportNativeTranslation = true),
    Language("id"),
    Language("pt-BR", "pt", disableTranslator = true, supportNativeTranslation = true),
)
