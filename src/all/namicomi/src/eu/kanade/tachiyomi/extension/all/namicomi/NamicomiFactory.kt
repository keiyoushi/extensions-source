package eu.kanade.tachiyomi.extension.all.namicomi

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NamicomiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NamicomiEnglish(),
        NamicomiArabic(),
        NamicomiBulgarian(),
        NamicomiCatalan(),
        NamicomiChineseSimplified(),
        NamicomiChineseTraditional(),
        NamicomiCroatian(),
        NamicomiCzech(),
        NamicomiDanish(),
        NamicomiDutch(),
        NamicomiEstonian(),
        NamicomiFilipino(),
        NamicomiFinnish(),
        NamicomiFrench(),
        NamicomiGerman(),
        NamicomiGreek(),
        NamicomiHebrew(),
        NamicomiHindi(),
        NamicomiHungarian(),
        NamicomiIcelandic(),
        NamicomiIrish(),
        NamicomiIndonesian(),
        NamicomiItalian(),
        NamicomiJapanese(),
        NamicomiKorean(),
        NamicomiLithuanian(),
        NamicomiMalay(),
        NamicomiNepali(),
        NamicomiNorwegian(),
        NamicomiPanjabi(),
        NamicomiPersian(),
        NamicomiPolish(),
        NamicomiPortugueseBrazil(),
        NamicomiPortuguesePortugal(),
        NamicomiRussian(),
        NamicomiSlovak(),
        NamicomiSlovenian(),
        NamicomiSpanishLatinAmerica(),
        NamicomiSpanishSpain(),
        NamicomiSwedish(),
        NamicomiThai(),
        NamicomiTurkish(),
        NamicomiUkrainian(),
    )
}

class NamicomiArabic : Namicomi("ar")
class NamicomiBulgarian : Namicomi("bg")
class NamicomiCatalan : Namicomi("ca")
class NamicomiChineseSimplified : Namicomi("zh-Hans", "zh-hans")
class NamicomiChineseTraditional : Namicomi("zh-Hant", "zh-hant")
class NamicomiCroatian : Namicomi("hr")
class NamicomiCzech : Namicomi("cs")
class NamicomiDanish : Namicomi("da")
class NamicomiDutch : Namicomi("nl")
class NamicomiEnglish : Namicomi("en")
class NamicomiEstonian : Namicomi("et")
class NamicomiFilipino : Namicomi("fil")
class NamicomiFinnish : Namicomi("fi")
class NamicomiFrench : Namicomi("fr")
class NamicomiGerman : Namicomi("de")
class NamicomiGreek : Namicomi("el")
class NamicomiHebrew : Namicomi("he")
class NamicomiHindi : Namicomi("hi")
class NamicomiHungarian : Namicomi("hu")
class NamicomiIcelandic : Namicomi("is")
class NamicomiIrish : Namicomi("ga")
class NamicomiIndonesian : Namicomi("id")
class NamicomiItalian : Namicomi("it")
class NamicomiJapanese : Namicomi("ja")
class NamicomiKorean : Namicomi("ko")
class NamicomiLithuanian : Namicomi("lt")
class NamicomiMalay : Namicomi("ms")
class NamicomiNepali : Namicomi("ne")
class NamicomiNorwegian : Namicomi("no")
class NamicomiPanjabi : Namicomi("pa")
class NamicomiPersian : Namicomi("fa")
class NamicomiPolish : Namicomi("pl")
class NamicomiPortugueseBrazil : Namicomi("pt-BR", "pt-br")
class NamicomiPortuguesePortugal : Namicomi("pt", "pt-pt")
class NamicomiRussian : Namicomi("ru")
class NamicomiSlovak : Namicomi("sk")
class NamicomiSlovenian : Namicomi("sl")
class NamicomiSpanishLatinAmerica : Namicomi("es-419")
class NamicomiSpanishSpain : Namicomi("es", "es-es")
class NamicomiSwedish : Namicomi("sv")
class NamicomiThai : Namicomi("th")
class NamicomiTurkish : Namicomi("tr")
class NamicomiUkrainian : Namicomi("uk")
