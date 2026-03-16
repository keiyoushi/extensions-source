package eu.kanade.tachiyomi.extension.all.weebdex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WeebDexFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        WeebDexAll(),
        WeebDexAfrikaans(),
        WeebDexAlbanian(),
        WeebDexArabic(),
        WeebDexAzerbaijani(),
        WeebDexBasque(),
        WeebDexBelarusian(),
        WeebDexBengali(),
        WeebDexBulgarian(),
        WeebDexBurmese(),
        WeebDexCatalan(),
        WeebDexChineseSimplified(),
        WeebDexChineseTraditional(),
        WeebDexChuvash(),
        WeebDexCroatian(),
        WeebDexCzech(),
        WeebDexDanish(),
        WeebDexDutch(),
        WeebDexEnglish(),
        WeebDexEsperanto(),
        WeebDexEstonian(),
        WeebDexFilipino(),
        WeebDexFinnish(),
        WeebDexFrench(),
        WeebDexGeorgian(),
        WeebDexGerman(),
        WeebDexGreek(),
        WeebDexHebrew(),
        WeebDexHindi(),
        WeebDexHungarian(),
        WeebDexIndonesian(),
        WeebDexIrish(),
        WeebDexItalian(),
        WeebDexJapanese(),
        WeebDexJavanese(),
        WeebDexKazakh(),
        WeebDexKorean(),
        WeebDexLatin(),
        WeebDexLithuanian(),
        WeebDexMalay(),
        WeebDexMongolian(),
        WeebDexNepali(),
        WeebDexNorwegian(),
        WeebDexPersian(),
        WeebDexPolish(),
        WeebDexPortuguese(),
        WeebDexPortugueseBrazil(),
        WeebDexRomanian(),
        WeebDexRussian(),
        WeebDexSerbian(),
        WeebDexSlovak(),
        WeebDexSlovenian(),
        WeebDexSpanish(),
        WeebDexSpanishLatinAmerica(),
        WeebDexSwedish(),
        WeebDexTamil(),
        WeebDexTelugu(),
        WeebDexThai(),
        WeebDexTurkish(),
        WeebDexUkrainian(),
        WeebDexUrdu(),
        WeebDexUzbek(),
        WeebDexVietnamese(),
    )
}

class WeebDexAll : WeebDex("all", "all")
class WeebDexAfrikaans : WeebDex("af")
class WeebDexAlbanian : WeebDex("sq")
class WeebDexArabic : WeebDex("ar")
class WeebDexAzerbaijani : WeebDex("az")
class WeebDexBasque : WeebDex("eu")
class WeebDexBelarusian : WeebDex("be")
class WeebDexBengali : WeebDex("bn")
class WeebDexBulgarian : WeebDex("bg")
class WeebDexBurmese : WeebDex("my")
class WeebDexCatalan : WeebDex("ca")
class WeebDexChineseSimplified : WeebDex("zh-Hans", "zh")
class WeebDexChineseTraditional : WeebDex("zh-Hant", "zh-hk")
class WeebDexChuvash : WeebDex("cv")
class WeebDexCroatian : WeebDex("hr")
class WeebDexCzech : WeebDex("cs")
class WeebDexDanish : WeebDex("da")
class WeebDexDutch : WeebDex("nl")
class WeebDexEnglish : WeebDex("en")
class WeebDexEsperanto : WeebDex("eo")
class WeebDexEstonian : WeebDex("et")
class WeebDexFilipino : WeebDex("fil", "tl")
class WeebDexFinnish : WeebDex("fi")
class WeebDexFrench : WeebDex("fr")
class WeebDexGeorgian : WeebDex("ka")
class WeebDexGerman : WeebDex("de")
class WeebDexGreek : WeebDex("el")
class WeebDexHebrew : WeebDex("he")
class WeebDexHindi : WeebDex("hi")
class WeebDexHungarian : WeebDex("hu")
class WeebDexIndonesian : WeebDex("id")
class WeebDexIrish : WeebDex("ga")
class WeebDexItalian : WeebDex("it")
class WeebDexJapanese : WeebDex("ja")
class WeebDexJavanese : WeebDex("jv")
class WeebDexKazakh : WeebDex("kk")
class WeebDexKorean : WeebDex("ko")
class WeebDexLatin : WeebDex("la")
class WeebDexLithuanian : WeebDex("lt")
class WeebDexMalay : WeebDex("ms")
class WeebDexMongolian : WeebDex("mn")
class WeebDexNepali : WeebDex("ne")
class WeebDexNorwegian : WeebDex("no")
class WeebDexPersian : WeebDex("fa")
class WeebDexPolish : WeebDex("pl")
class WeebDexPortuguese : WeebDex("pt")
class WeebDexPortugueseBrazil : WeebDex("pt-BR", "pt-br")
class WeebDexRomanian : WeebDex("ro")
class WeebDexRussian : WeebDex("ru")
class WeebDexSerbian : WeebDex("sr")
class WeebDexSlovak : WeebDex("sk")
class WeebDexSlovenian : WeebDex("sl")
class WeebDexSpanish : WeebDex("es")
class WeebDexSpanishLatinAmerica : WeebDex("es-419", "es-la")
class WeebDexSwedish : WeebDex("sv")
class WeebDexTamil : WeebDex("ta", "tam")
class WeebDexTelugu : WeebDex("te")
class WeebDexThai : WeebDex("th")
class WeebDexTurkish : WeebDex("tr")
class WeebDexUkrainian : WeebDex("uk")
class WeebDexUrdu : WeebDex("ur")
class WeebDexUzbek : WeebDex("uz")
class WeebDexVietnamese : WeebDex("vi")
