package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NamiComiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NamiComiEnglish(),
        NamiComiArabic(),
        NamiComiBulgarian(),
        NamiComiCatalan(),
        NamiComiChineseSimplified(),
        NamiComiChineseTraditional(),
        NamiComiCroatian(),
        NamiComiCzech(),
        NamiComiDanish(),
        NamiComiDutch(),
        NamiComiEstonian(),
        NamiComiFilipino(),
        NamiComiFinnish(),
        NamiComiFrench(),
        NamiComiGerman(),
        NamiComiGreek(),
        NamiComiHebrew(),
        NamiComiHindi(),
        NamiComiHungarian(),
        NamiComiIcelandic(),
        NamiComiIrish(),
        NamiComiIndonesian(),
        NamiComiItalian(),
        NamiComiJapanese(),
        NamiComiKorean(),
        NamiComiLithuanian(),
        NamiComiMalay(),
        NamiComiNepali(),
        NamiComiNorwegian(),
        NamiComiPanjabi(),
        NamiComiPersian(),
        NamiComiPolish(),
        NamiComiPortugueseBrazil(),
        NamiComiPortuguesePortugal(),
        NamiComiRussian(),
        NamiComiSlovak(),
        NamiComiSlovenian(),
        NamiComiSpanishLatinAmerica(),
        NamiComiSpanishSpain(),
        NamiComiSwedish(),
        NamiComiThai(),
        NamiComiTurkish(),
        NamiComiUkrainian(),
    )
}

class NamiComiArabic : NamiComi("ar")
class NamiComiBulgarian : NamiComi("bg")
class NamiComiCatalan : NamiComi("ca")
class NamiComiChineseSimplified : NamiComi("zh-Hans", "zh-hans")
class NamiComiChineseTraditional : NamiComi("zh-Hant", "zh-hant")
class NamiComiCroatian : NamiComi("hr")
class NamiComiCzech : NamiComi("cs")
class NamiComiDanish : NamiComi("da")
class NamiComiDutch : NamiComi("nl")
class NamiComiEnglish : NamiComi("en")
class NamiComiEstonian : NamiComi("et")
class NamiComiFilipino : NamiComi("fil")
class NamiComiFinnish : NamiComi("fi")
class NamiComiFrench : NamiComi("fr")
class NamiComiGerman : NamiComi("de")
class NamiComiGreek : NamiComi("el")
class NamiComiHebrew : NamiComi("he")
class NamiComiHindi : NamiComi("hi")
class NamiComiHungarian : NamiComi("hu")
class NamiComiIcelandic : NamiComi("is")
class NamiComiIrish : NamiComi("ga")
class NamiComiIndonesian : NamiComi("id")
class NamiComiItalian : NamiComi("it")
class NamiComiJapanese : NamiComi("ja")
class NamiComiKorean : NamiComi("ko")
class NamiComiLithuanian : NamiComi("lt")
class NamiComiMalay : NamiComi("ms")
class NamiComiNepali : NamiComi("ne")
class NamiComiNorwegian : NamiComi("no")
class NamiComiPanjabi : NamiComi("pa")
class NamiComiPersian : NamiComi("fa")
class NamiComiPolish : NamiComi("pl")
class NamiComiPortugueseBrazil : NamiComi("pt-BR", "pt-br")
class NamiComiPortuguesePortugal : NamiComi("pt", "pt-pt")
class NamiComiRussian : NamiComi("ru")
class NamiComiSlovak : NamiComi("sk")
class NamiComiSlovenian : NamiComi("sl")
class NamiComiSpanishLatinAmerica : NamiComi("es-419")
class NamiComiSpanishSpain : NamiComi("es", "es-es")
class NamiComiSwedish : NamiComi("sv")
class NamiComiThai : NamiComi("th")
class NamiComiTurkish : NamiComi("tr")
class NamiComiUkrainian : NamiComi("uk")
