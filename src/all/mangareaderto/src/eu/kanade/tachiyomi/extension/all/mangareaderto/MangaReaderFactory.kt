package eu.kanade.tachiyomi.extension.all.mangareaderto

import eu.kanade.tachiyomi.source.SourceFactory

class MangaReaderFactory : SourceFactory {
    override fun createSources() =
        arrayOf(
            Language("en"),
            Language("es", chapterInfix = "es-mx"),
            Language("fr"),
            Language("ja"),
            Language("ko"),
            Language("pt-BR", infix = "pt"),
            Language("zh"),
        ).map(::MangaReader)
}

data class Language(
    val code: String,
    val infix: String = code,
    val chapterInfix: String = code.lowercase(),
)
