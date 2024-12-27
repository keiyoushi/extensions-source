package eu.kanade.tachiyomi.extension.all.snowmtl

import android.os.Build
import androidx.annotation.RequiresApi
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@RequiresApi(Build.VERSION_CODES.O)
class SnowmtlFactory : SourceFactory {
    override fun createSources(): List<Source> = languageList.map(::Snowmtl)
}

data class Source(val lang: String, val target: String = lang, val origin: String = "en")

private val languageList = listOf(
    Source("en"),
    Source("es"),
    Source("id"),
    Source("it"),
    Source("pt-BR", "pt"),
)
