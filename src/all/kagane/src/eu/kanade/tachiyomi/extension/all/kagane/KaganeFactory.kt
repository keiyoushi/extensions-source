package eu.kanade.tachiyomi.extension.all.kagane

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class KaganeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KaganeEnglish(),
        KaganeJapanese(),
        KaganeKorean(),
        KaganeChinese(),
        KaganeSpanish(),
        KaganeSpanishLatAm(),
        KaganeFrench(),
        KaganeGerman(),
        KaganePortuguese(),
        KaganePortugueseBrazil(),
        KaganeRussian(),
        KaganeItalian(),
        KaganeIndonesian(),
        KaganeVietnamese(),
        KaganeThai(),
        KaganePolish(),
        KaganeHindi(),
        KaganeArabic(),
    )
}

class KaganeEnglish : Kagane("en", listOf("en"))
class KaganeJapanese : Kagane("ja", listOf("ja"))
class KaganeKorean : Kagane("ko", listOf("ko"))
class KaganeChinese : Kagane("zh", listOf("zh-Hans", "zh-Hant"))
class KaganeSpanish : Kagane("es", listOf("es"))
class KaganeSpanishLatAm : Kagane("es-419", listOf("es-419"))
class KaganeFrench : Kagane("fr", listOf("fr"))
class KaganeGerman : Kagane("de", listOf("de"))
class KaganePortuguese : Kagane("pt", listOf("pt"))
class KaganePortugueseBrazil : Kagane("pt-BR", listOf("pt-BR"))
class KaganeRussian : Kagane("ru", listOf("ru"))
class KaganeItalian : Kagane("it", listOf("it"))
class KaganeIndonesian : Kagane("id", listOf("id"))
class KaganeVietnamese : Kagane("vi", listOf("vi"))
class KaganeThai : Kagane("th", listOf("th"))
class KaganePolish : Kagane("pl", listOf("pl"))
class KaganeHindi : Kagane("hi", listOf("hi"))
class KaganeArabic : Kagane("ar", listOf("ar"))
