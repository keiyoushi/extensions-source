package eu.kanade.tachiyomi.extension.all.ehentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        EHentaiJa(),
        EHentaiEn(),
        EHentaiZh(),
        EHentaiNl(),
        EHentaiFr(),
        EHentaiDe(),
        EHentaiHu(),
        EHentaiIt(),
        EHentaiKo(),
        EHentaiPl(),
        EHentaiPtBr(),
        EHentaiRu(),
        EHentaiEs(),
        EHentaiTh(),
        EHentaiVi(),
        EHentaiNone(),
        EHentaiOther(),
    )
}

class EHentaiJa : EHentai("ja", "japanese")
class EHentaiEn : EHentai("en", "english")
class EHentaiZh : EHentai("zh", "chinese")
class EHentaiNl : EHentai("nl", "dutch")
class EHentaiFr : EHentai("fr", "french")
class EHentaiDe : EHentai("de", "german")
class EHentaiHu : EHentai("hu", "hungarian")
class EHentaiIt : EHentai("it", "italian")
class EHentaiKo : EHentai("ko", "korean")
class EHentaiPl : EHentai("pl", "polish")
class EHentaiPtBr : EHentai("pt-BR", "portuguese") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7151438547982231541
}
class EHentaiRu : EHentai("ru", "russian")
class EHentaiEs : EHentai("es", "spanish")
class EHentaiTh : EHentai("th", "thai")
class EHentaiVi : EHentai("vi", "vietnamese")
class EHentaiNone : EHentai("none", "n/a")
class EHentaiOther : EHentai("other", "other")
