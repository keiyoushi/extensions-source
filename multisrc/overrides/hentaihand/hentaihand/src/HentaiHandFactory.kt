package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import okhttp3.OkHttpClient

class HentaiHandFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        // https://hentaihand.com/api/languages?per_page=50
        HentaiHandOther(),
        HentaiHandEn(),
        HentaiHandZh(),
        HentaiHandJa(),
        HentaiHandNoText(),
        HentaiHandEo(),
        HentaiHandCeb(),
        HentaiHandCs(),
        HentaiHandAr(),
        HentaiHandSk(),
        HentaiHandMn(),
        HentaiHandUk(),
        HentaiHandLa(),
        HentaiHandTl(),
        HentaiHandEs(),
        HentaiHandIt(),
        HentaiHandKo(),
        HentaiHandTh(),
        HentaiHandPl(),
        HentaiHandFr(),
        HentaiHandPtBr(),
        HentaiHandDe(),
        HentaiHandFi(),
        HentaiHandRu(),
        HentaiHandHu(),
        HentaiHandId(),
        HentaiHandVi(),
        HentaiHandNl(),
        HentaiHandHi(),
        HentaiHandTr(),
        HentaiHandEl(),
        HentaiHandSr(),
        HentaiHandJv(),
        HentaiHandBg(),
    )
}
abstract class HentaiHandCommon(
    override val lang: String,
    hhLangId: List<Int> = emptyList(),
    // altLangId: Int? = null
) : HentaiHand("HentaiHand", "https://hentaihand.com", lang, false, hhLangId) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}

class HentaiHandOther : HentaiHandCommon("all") {
    override val id: Long = 1235047015955289468
}
class HentaiHandJa : HentaiHandCommon("ja", listOf(1, 29))
class HentaiHandEn : HentaiHandCommon("en", listOf(2, 27))
class HentaiHandZh : HentaiHandCommon("zh", listOf(3, 50))
class HentaiHandBg : HentaiHandCommon("bg", listOf(4))
class HentaiHandCeb : HentaiHandCommon("ceb", listOf(5, 44))
class HentaiHandNoText : HentaiHandCommon("other", listOf(6)) {
    override val id: Long = 7302549142935671434
}
class HentaiHandTl : HentaiHandCommon("tl", listOf(7, 55))
class HentaiHandAr : HentaiHandCommon("ar", listOf(8, 49))
class HentaiHandEl : HentaiHandCommon("el", listOf(9))
class HentaiHandSr : HentaiHandCommon("sr", listOf(10))
class HentaiHandJv : HentaiHandCommon("jv", listOf(11, 51))
class HentaiHandUk : HentaiHandCommon("uk", listOf(12, 46))
class HentaiHandTr : HentaiHandCommon("tr", listOf(13, 41))
class HentaiHandFi : HentaiHandCommon("fi", listOf(14, 54))
class HentaiHandLa : HentaiHandCommon("la", listOf(15))
class HentaiHandMn : HentaiHandCommon("mn", listOf(16))
class HentaiHandEo : HentaiHandCommon("eo", listOf(17, 47))
class HentaiHandSk : HentaiHandCommon("sk", listOf(18))
class HentaiHandCs : HentaiHandCommon("cs", listOf(19, 52))
class HentaiHandKo : HentaiHandCommon("ko", listOf(30, 39))
class HentaiHandRu : HentaiHandCommon("ru", listOf(31))
class HentaiHandIt : HentaiHandCommon("it", listOf(32))
class HentaiHandEs : HentaiHandCommon("es", listOf(33, 37))
class HentaiHandPtBr : HentaiHandCommon("pt-BR", listOf(34)) {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 2516244587139644000
}
class HentaiHandTh : HentaiHandCommon("th", listOf(35, 40))
class HentaiHandFr : HentaiHandCommon("fr", listOf(36))
class HentaiHandId : HentaiHandCommon("id", listOf(38))
class HentaiHandVi : HentaiHandCommon("vi", listOf(42))
class HentaiHandDe : HentaiHandCommon("de", listOf(43))
class HentaiHandPl : HentaiHandCommon("pl", listOf(45))
class HentaiHandHu : HentaiHandCommon("hu", listOf(48))
class HentaiHandNl : HentaiHandCommon("nl", listOf(53))
class HentaiHandHi : HentaiHandCommon("hi", listOf(56))
