package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.multisrc.machinetranslations.Language
import eu.kanade.tachiyomi.source.SourceFactory

@RequiresApi(Build.VERSION_CODES.O)
class SnowmtlFactory : SourceFactory {
    override fun createSources() = languageList.map(::Snowmtl)
}

private val languageList = listOf(
    Language("ar", disableSourceSettings = true),
    Language("en"),
    Language("es"),
    Language("fr"),
    Language("id"),
    Language("it"),
    Language("pt-BR", "pt"),
)
