package eu.kanade.tachiyomi.extension.all.manhuarm

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.source.SourceFactory

@RequiresApi(Build.VERSION_CODES.O)
class ManhuarmFactory : SourceFactory {
    override fun createSources() = languageList.map(::Manhuarm)
}

private val languageList = listOf(
    Language("ar", disableFontSettings = true),
    Language("en"),
    Language("es"),
    Language("fr", supportNativeTranslation = true),
    Language("id", supportNativeTranslation = true),
    Language("it"),
    Language("pt-BR", "pt", supportNativeTranslation = true),
)
