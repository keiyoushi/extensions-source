package eu.kanade.tachiyomi.extension.all.mangadex

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class MangaDexFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        MangaDexEnglish(),
        MangaDexAlbanian(),
        MangaDexArabic(),
        MangaDexAzerbaijani(),
        MangaDexBengali(),
        MangaDexBulgarian(),
        MangaDexBurmese(),
        MangaDexCatalan(),
        MangaDexChineseSimplified(),
        MangaDexChineseTraditional(),
        MangaDexCroatian(),
        MangaDexCzech(),
        MangaDexDanish(),
        MangaDexDutch(),
        MangaDexEsperanto(),
        MangaDexEstonian(),
        MangaDexFilipino(),
        MangaDexFinnish(),
        MangaDexFrench(),
        MangaDexGeorgian(),
        MangaDexGerman(),
        MangaDexGreek(),
        MangaDexHebrew(),
        MangaDexHindi(),
        MangaDexHungarian(),
        MangaDexIndonesian(),
        MangaDexItalian(),
        MangaDexJapanese(),
        MangaDexKazakh(),
        MangaDexKorean(),
        MangaDexLatin(),
        MangaDexLithuanian(),
        MangaDexMalay(),
        MangaDexMongolian(),
        MangaDexNepali(),
        MangaDexNorwegian(),
        MangaDexPersian(),
        MangaDexPolish(),
        MangaDexPortugueseBrazil(),
        MangaDexPortuguesePortugal(),
        MangaDexRomanian(),
        MangaDexRussian(),
        MangaDexSerbian(),
        MangaDexSlovak(),
        MangaDexSpanishLatinAmerica(),
        MangaDexSpanishSpain(),
        MangaDexSwedish(),
        MangaDexTamil(),
        MangaDexTelugu(),
        MangaDexThai(),
        MangaDexTurkish(),
        MangaDexUkrainian(),
        MangaDexVietnamese(),
    )
}

class MangaDexAlbanian : MangaDex("sq")
class MangaDexArabic : MangaDex("ar")
class MangaDexAzerbaijani : MangaDex("az")
class MangaDexBengali : MangaDex("bn")
class MangaDexBulgarian : MangaDex("bg")
class MangaDexBurmese : MangaDex("my")
class MangaDexCatalan : MangaDex("ca")
class MangaDexChineseSimplified : MangaDex("zh-Hans", "zh")
class MangaDexChineseTraditional : MangaDex("zh-Hant", "zh-hk")
class MangaDexCroatian : MangaDex("hr")
class MangaDexCzech : MangaDex("cs")
class MangaDexDanish : MangaDex("da")
class MangaDexDutch : MangaDex("nl")
class MangaDexEnglish : MangaDex("en")
class MangaDexEsperanto : MangaDex("eo")
class MangaDexEstonian : MangaDex("et")
class MangaDexFilipino : MangaDex("fil", "tl")
class MangaDexFinnish : MangaDex("fi")
class MangaDexFrench : MangaDex("fr")
class MangaDexGeorgian : MangaDex("ka")
class MangaDexGerman : MangaDex("de")
class MangaDexGreek : MangaDex("el")
class MangaDexHebrew : MangaDex("he")
class MangaDexHindi : MangaDex("hi")
class MangaDexHungarian : MangaDex("hu")
class MangaDexIndonesian : MangaDex("id")
class MangaDexItalian : MangaDex("it")
class MangaDexJapanese : MangaDex("ja")
class MangaDexKazakh : MangaDex("kk")
class MangaDexKorean : MangaDex("ko")
class MangaDexLatin : MangaDex("la")
class MangaDexLithuanian : MangaDex("lt")
class MangaDexMalay : MangaDex("ms")
class MangaDexMongolian : MangaDex("mn")
class MangaDexNepali : MangaDex("ne")
class MangaDexNorwegian : MangaDex("no")
class MangaDexPersian : MangaDex("fa")
class MangaDexPolish : MangaDex("pl")
class MangaDexPortugueseBrazil : MangaDex("pt-BR", "pt-br")
class MangaDexPortuguesePortugal : MangaDex("pt")
class MangaDexRomanian : MangaDex("ro")
class MangaDexRussian : MangaDex("ru")
class MangaDexSerbian : MangaDex("sr")
class MangaDexSlovak : MangaDex("sk")
class MangaDexSpanishLatinAmerica : MangaDex("es-419", "es-la")
class MangaDexSpanishSpain : MangaDex("es")
class MangaDexSwedish : MangaDex("sv")
class MangaDexTamil : MangaDex("ta")
class MangaDexTelugu : MangaDex("te")
class MangaDexThai : MangaDex("th")
class MangaDexTurkish : MangaDex("tr")
class MangaDexUkrainian : MangaDex("uk")
class MangaDexVietnamese : MangaDex("vi")
