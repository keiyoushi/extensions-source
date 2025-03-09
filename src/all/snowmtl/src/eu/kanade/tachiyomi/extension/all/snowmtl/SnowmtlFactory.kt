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
    LanguageSetting("ar", disableSourceSettings = true),
    LanguageSetting("en"),
    LanguageSetting("es"),
    LanguageSetting("id"),
    LanguageSetting("it"),
    LanguageSetting("pt-BR", "pt"),
)

data class LanguageSetting(
    override val lang: String,
    override val target: String = lang,
    override val origin: String = "en",
    override var fontSize: Int = 24,
    override var disableSourceSettings: Boolean = false,
) : Language
