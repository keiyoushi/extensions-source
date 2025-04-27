package eu.kanade.tachiyomi.extension.all.globalcomix

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class GlobalComixFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        GlobalComixAlbanian(),
        GlobalComixArabic(),
        GlobalComixBulgarian(),
        GlobalComixBengali(),
        GlobalComixBrazilianPortuguese(),
        GlobalComixChineseMandarin(),
        GlobalComixCzech(),
        GlobalComixGerman(),
        GlobalComixDanish(),
        GlobalComixGreek(),
        GlobalComixEnglish(),
        GlobalComixSpanish(),
        GlobalComixPersian(),
        GlobalComixFinnish(),
        GlobalComixFilipino(),
        GlobalComixFrench(),
        GlobalComixHindi(),
        GlobalComixHungarian(),
        GlobalComixIndonesian(),
        GlobalComixItalian(),
        GlobalComixHebrew(),
        GlobalComixJapanese(),
        GlobalComixKorean(),
        GlobalComixLatvian(),
        GlobalComixMalay(),
        GlobalComixDutch(),
        GlobalComixNorwegian(),
        GlobalComixPolish(),
        GlobalComixPortugese(),
        GlobalComixRomanian(),
        GlobalComixRussian(),
        GlobalComixSwedish(),
        GlobalComixSlovak(),
        GlobalComixSlovenian(),
        GlobalComixTamil(),
        GlobalComixThai(),
        GlobalComixTurkish(),
        GlobalComixUkrainian(),
        GlobalComixUrdu(),
        GlobalComixVietnamese(),
        GlobalComixChineseCantonese(),
    )
}

class GlobalComixAlbanian : GlobalComix("al")
class GlobalComixArabic : GlobalComix("ar")
class GlobalComixBulgarian : GlobalComix("bg")
class GlobalComixBengali : GlobalComix("bn")
class GlobalComixBrazilianPortuguese : GlobalComix("pt-BR", "br")
class GlobalComixChineseMandarin : GlobalComix("zh-Hans", "cn")
class GlobalComixCzech : GlobalComix("cs", "cz")
class GlobalComixGerman : GlobalComix("de")
class GlobalComixDanish : GlobalComix("dk")
class GlobalComixGreek : GlobalComix("el")
class GlobalComixEnglish : GlobalComix("en")
class GlobalComixSpanish : GlobalComix("es")
class GlobalComixPersian : GlobalComix("fa")
class GlobalComixFinnish : GlobalComix("fi")
class GlobalComixFilipino : GlobalComix("fil", "fo")
class GlobalComixFrench : GlobalComix("fr")
class GlobalComixHindi : GlobalComix("hi")
class GlobalComixHungarian : GlobalComix("hu")
class GlobalComixIndonesian : GlobalComix("id")
class GlobalComixItalian : GlobalComix("it")
class GlobalComixHebrew : GlobalComix("he", "iw")
class GlobalComixJapanese : GlobalComix("ja", "jp")
class GlobalComixKorean : GlobalComix("ko", "kr")
class GlobalComixLatvian : GlobalComix("lv")
class GlobalComixMalay : GlobalComix("ms", "my")
class GlobalComixDutch : GlobalComix("nl")
class GlobalComixNorwegian : GlobalComix("no")
class GlobalComixPolish : GlobalComix("pl")
class GlobalComixPortugese : GlobalComix("pt")
class GlobalComixRomanian : GlobalComix("ro")
class GlobalComixRussian : GlobalComix("ru")
class GlobalComixSwedish : GlobalComix("sv", "se")
class GlobalComixSlovak : GlobalComix("sk")
class GlobalComixSlovenian : GlobalComix("sl")
class GlobalComixTamil : GlobalComix("ta")
class GlobalComixThai : GlobalComix("th")
class GlobalComixTurkish : GlobalComix("tr")
class GlobalComixUkrainian : GlobalComix("uk", "ua")
class GlobalComixUrdu : GlobalComix("ur")
class GlobalComixVietnamese : GlobalComix("vi")
class GlobalComixChineseCantonese : GlobalComix("zh-Hant", "zh")
