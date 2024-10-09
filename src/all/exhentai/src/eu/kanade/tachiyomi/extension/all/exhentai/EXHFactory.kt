package eu.kanade.tachiyomi.extension.all.exhentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EXHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        EXHentaiJa(),
        EXHentaiEn(),
        EXHentaiZh(),
        EXHentaiNl(),
        EXHentaiFr(),
        EXHentaiDe(),
        EXHentaiHu(),
        EXHentaiIt(),
        EXHentaiKo(),
        EXHentaiPl(),
        EXHentaiPtBr(),
        EXHentaiRu(),
        EXHentaiEs(),
        EXHentaiTh(),
        EXHentaiVi(),
        EXHentaiNone(),
        EXHentaiOther(),
    )
}

class EXHentaiJa : EXHentai("ja", "japanese")
class EXHentaiEn : EXHentai("en", "english")
class EXHentaiZh : EXHentai("zh", "chinese")
class EXHentaiNl : EXHentai("nl", "dutch")
class EXHentaiFr : EXHentai("fr", "french")
class EXHentaiDe : EXHentai("de", "german")
class EXHentaiHu : EXHentai("hu", "hungarian")
class EXHentaiIt : EXHentai("it", "italian")
class EXHentaiKo : EXHentai("ko", "korean")
class EXHentaiPl : EXHentai("pl", "polish")
class EXHentaiPtBr : EXHentai("pt-BR", "portuguese") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7151438547982231541
}
class EXHentaiRu : EXHentai("ru", "russian")
class EXHentaiEs : EXHentai("es", "spanish")
class EXHentaiTh : EXHentai("th", "thai")
class EXHentaiVi : EXHentai("vi", "vietnamese")
class EXHentaiNone : EXHentai("none", "n/a")
class EXHentaiOther : EXHentai("other", "other")
