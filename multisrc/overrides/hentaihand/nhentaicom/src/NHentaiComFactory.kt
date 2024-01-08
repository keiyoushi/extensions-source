package eu.kanade.tachiyomi.extension.all.nhentaicom

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import okhttp3.OkHttpClient

class NHentaiComFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        // https://nhentai.com/api/languages?per_page=50
        NHentaiComAll(),
        NHentaiComEn(),
        NHentaiComZh(),
        NHentaiComJa(),
        NHentaiComNoText(),
        NHentaiComEo(),
        NHentaiComCeb(),
        NHentaiComCs(),
        NHentaiComAr(),
        NHentaiComSk(),
        NHentaiComMn(),
        NHentaiComUk(),
        NHentaiComLa(),
        NHentaiComTl(),
        NHentaiComEs(),
        NHentaiComIt(),
        NHentaiComKo(),
        NHentaiComTh(),
        NHentaiComPl(),
        NHentaiComFr(),
        NHentaiComPtBr(),
        NHentaiComDe(),
        NHentaiComFi(),
        NHentaiComRu(),
        NHentaiComHu(),
        NHentaiComId(),
        NHentaiComVi(),
        NHentaiComNl(),
        NHentaiComTr(),
        NHentaiComEl(),
        NHentaiComBg(),
        NHentaiComSr(),
        NHentaiComJv(),
        NHentaiComHi(),
    )
}
abstract class NHentaiComCommon(
    override val lang: String,
    hhLangId: List<Int> = emptyList(),
    // altLangId: Int? = null
) : HentaiHand("nHentai.com (unoriginal)", "https://nhentai.com", lang, false, hhLangId) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}

class NHentaiComAll : NHentaiComCommon("all") {
    override val id: Long = 9165839893600661480
}

class NHentaiComJa : NHentaiComCommon("ja", listOf(1, 29))
class NHentaiComEn : NHentaiComCommon("en", listOf(2, 27)) {
    override val id: Long = 5591830863732393712
}
class NHentaiComZh : NHentaiComCommon("zh", listOf(3, 50))
class NHentaiComBg : NHentaiComCommon("bg", listOf(4))
class NHentaiComCeb : NHentaiComCommon("ceb", listOf(5, 44))
class NHentaiComNoText : NHentaiComCommon("other", listOf(6)) {
    override val id: Long = 5817327335315373850
}
class NHentaiComTl : NHentaiComCommon("tl", listOf(7, 55))
class NHentaiComAr : NHentaiComCommon("ar", listOf(8, 49))
class NHentaiComEl : NHentaiComCommon("el", listOf(9))
class NHentaiComSr : NHentaiComCommon("sr", listOf(10))
class NHentaiComJv : NHentaiComCommon("jv", listOf(11, 51))
class NHentaiComUk : NHentaiComCommon("uk", listOf(12, 46))
class NHentaiComTr : NHentaiComCommon("tr", listOf(13, 41))
class NHentaiComFi : NHentaiComCommon("fi", listOf(14, 54))
class NHentaiComLa : NHentaiComCommon("la", listOf(15))
class NHentaiComMn : NHentaiComCommon("mn", listOf(16))
class NHentaiComEo : NHentaiComCommon("eo", listOf(17, 47))
class NHentaiComSk : NHentaiComCommon("sk", listOf(18))
class NHentaiComCs : NHentaiComCommon("cs", listOf(19, 52)) {
    override val id: Long = 1144495813995437124
}
class NHentaiComKo : NHentaiComCommon("ko", listOf(30, 39))
class NHentaiComRu : NHentaiComCommon("ru", listOf(31))
class NHentaiComIt : NHentaiComCommon("it", listOf(32))
class NHentaiComEs : NHentaiComCommon("es", listOf(33, 37))
class NHentaiComPtBr : NHentaiComCommon("pt-BR", listOf(34))
class NHentaiComTh : NHentaiComCommon("th", listOf(35, 40))
class NHentaiComFr : NHentaiComCommon("fr", listOf(36))
class NHentaiComId : NHentaiComCommon("id", listOf(38))
class NHentaiComVi : NHentaiComCommon("vi", listOf(42))
class NHentaiComDe : NHentaiComCommon("de", listOf(43))
class NHentaiComPl : NHentaiComCommon("pl", listOf(45))
class NHentaiComHu : NHentaiComCommon("hu", listOf(48))
class NHentaiComNl : NHentaiComCommon("nl", listOf(53))
class NHentaiComHi : NHentaiComCommon("hi", listOf(56))
