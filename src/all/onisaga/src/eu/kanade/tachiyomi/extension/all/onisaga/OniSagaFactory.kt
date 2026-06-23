package eu.kanade.tachiyomi.extension.all.onisaga

import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.online.HttpSource

class OniSagaFactory : SourceFactory {
    override fun createSources(): List<HttpSource> = listOf(
        OniSaga("all", null),
        OniSaga("en", "EN"),
        OniSaga("fr", "FR"),
        OniSaga("ja", "JA"),
        OniSaga("pt-BR", "PT-BR"),
        OniSaga("pt", "PT"),
        OniSaga("es-419", "ES-LA"),
        OniSaga("es", "ES"),
    )
}
