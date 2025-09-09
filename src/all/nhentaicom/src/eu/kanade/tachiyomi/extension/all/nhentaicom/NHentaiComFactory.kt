package eu.kanade.tachiyomi.extension.all.nhentaicom

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import okhttp3.OkHttpClient

class NHentaiComFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        // https://nhentai.com/api/languages?per_page=50
        NHentaiComAll(),
        NHentaiComZh(),
        NHentaiComEn(),
        NHentaiComJa(),
        NHentaiComNoText(),
        NHentaiComAr(),
        NHentaiComJv(),
        NHentaiComBg(),
        NHentaiComCs(),
        NHentaiComUk(),
        NHentaiComSk(),
        NHentaiComEo(),
        NHentaiComMn(),
        NHentaiComLa(),
        NHentaiComCeb(),
        NHentaiComTl(),
        NHentaiComFi(),
        NHentaiComTr(),
        NHentaiComSr(),
        NHentaiComEl(),
        NHentaiComKo(),
        NHentaiComRo(),
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

class NHentaiComZh : NHentaiComCommon("zh", listOf(1))
class NHentaiComEn : NHentaiComCommon("en", listOf(2)) {
    override val id: Long = 5591830863732393712
}
class NHentaiComJa : NHentaiComCommon("ja", listOf(3))
class NHentaiComNoText : NHentaiComCommon("other", listOf(4)) {
    override val id: Long = 5817327335315373850
}
class NHentaiComAr : NHentaiComCommon("ar", listOf(5))
class NHentaiComJv : NHentaiComCommon("jv", listOf(6))
class NHentaiComBg : NHentaiComCommon("bg", listOf(7))
class NHentaiComCs : NHentaiComCommon("cs", listOf(8)) {
    override val id: Long = 1144495813995437124
}
class NHentaiComUk : NHentaiComCommon("uk", listOf(9))
class NHentaiComSk : NHentaiComCommon("sk", listOf(10))
class NHentaiComEo : NHentaiComCommon("eo", listOf(11))
class NHentaiComMn : NHentaiComCommon("mn", listOf(12))
class NHentaiComLa : NHentaiComCommon("la", listOf(13))
class NHentaiComCeb : NHentaiComCommon("ceb", listOf(14))
class NHentaiComTl : NHentaiComCommon("tl", listOf(15))
class NHentaiComFi : NHentaiComCommon("fi", listOf(16))
class NHentaiComTr : NHentaiComCommon("tr", listOf(17))
class NHentaiComSr : NHentaiComCommon("sr", listOf(18))
class NHentaiComEl : NHentaiComCommon("el", listOf(19))
class NHentaiComKo : NHentaiComCommon("ko", listOf(20))
class NHentaiComRo : NHentaiComCommon("ro", listOf(21))
