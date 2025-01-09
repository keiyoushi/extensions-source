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
    Language("en"),
    Language("fr"),
    Language("pt-BR", "pt"),
)
