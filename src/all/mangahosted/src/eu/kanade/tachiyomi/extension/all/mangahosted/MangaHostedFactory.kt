package eu.kanade.tachiyomi.extension.all.mangahosted

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaHostedFactory : SourceFactory {
    override fun createSources(): List<Source> = languages.map { MangaHosted(it) }
}

class LanguageOption(
    val lang: String,
    val infix: String = lang,
    val mangaSubstring: String = infix,
    val nameSuffix: String = "",
    val orderBy: String = "DESC",
)

val languages = listOf(
    LanguageOption("en", "manga", "scan"),
    LanguageOption("en", "manga-v2", "kaka", " v2"),
    LanguageOption("en", "comic", "comic-dc", " Comics"),
    LanguageOption("es", "manga-spanish", "manga-es"),
    LanguageOption("id", "manga-indo", "id"),
    LanguageOption("it", "manga-italia", "manga-it"),
    LanguageOption("ja", "mangaraw", "raw"),
    LanguageOption("pt-BR", "manga-br"),
    LanguageOption("ru", "manga-ru", "mangaru"),
    LanguageOption("ru", "manga-ru-hentai", "hentai", " +18"),
    LanguageOption("ru", "manga-ru-yaoi", "yaoi", " +18 Yaoi"),
)
