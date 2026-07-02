package eu.kanade.tachiyomi.extension.all.nhentaicom

import eu.kanade.tachiyomi.multisrc.hentaihand.HentaiHand
import keiyoushi.annotation.Source
import okhttp3.OkHttpClient

@Source
abstract class NHentaiCom : HentaiHand() {

    override val chapters = false

    override val hhLangId = when (lang) {
        "all" -> emptyList()
        "zh" -> listOf(1)
        "en" -> listOf(2)
        "ja" -> listOf(3)
        "other" -> listOf(4)
        "ar" -> listOf(5)
        "jv" -> listOf(6)
        "bg" -> listOf(7)
        "cs" -> listOf(8)
        "uk" -> listOf(9)
        "sk" -> listOf(10)
        "eo" -> listOf(11)
        "mn" -> listOf(12)
        "la" -> listOf(13)
        "ceb" -> listOf(14)
        "tl" -> listOf(15)
        "fi" -> listOf(16)
        "tr" -> listOf(17)
        "sr" -> listOf(18)
        "el" -> listOf(19)
        "ko" -> listOf(20)
        "ro" -> listOf(21)
        else -> emptyList()
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { authIntercept(it) }
        .build()
}
