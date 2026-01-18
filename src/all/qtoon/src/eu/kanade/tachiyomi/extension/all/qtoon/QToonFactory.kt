package eu.kanade.tachiyomi.extension.all.qtoon

import eu.kanade.tachiyomi.source.SourceFactory

class QToonFactory : SourceFactory {
    override fun createSources() = listOf(
        QToon("en", "en-US"),
        QToon("es", "es-ES"),
        QToon("pt-BR", "pt-PT"),
    )
}
