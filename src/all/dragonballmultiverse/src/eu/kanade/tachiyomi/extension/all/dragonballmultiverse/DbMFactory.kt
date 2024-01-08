@file:Suppress("ClassName")

package eu.kanade.tachiyomi.extension.all.dragonballmultiverse

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DbMFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DbMultiverseEN(),
        DbMultiverseFR(),
        DbMultiverseJP(),
        DbMultiverseCN(),
        DbMultiverseES(),
        DbMultiverseIT(),
        DbMultiversePT(),
        DbMultiverseDE(),
        DbMultiversePL(),
        DbMultiverseNL(),
        DbMultiverseFR_PA(),
        DbMultiverseTR_TR(),
        DbMultiversePT_BR(),
        DbMultiverseHU_HU(),
        DbMultiverseGA_ES(),
        DbMultiverseCT_CT(),
        DbMultiverseNO_NO(),
        DbMultiverseRU_RU(),
        DbMultiverseRO_RO(),
        DbMultiverseEU_EH(),
        DbMultiverseLT_LT(),
        DbMultiverseHR_HR(),
        DbMultiverseKR_KR(),
        DbMultiverseFI_FI(),
        DbMultiverseHE_HE(),
        DbMultiverseBG_BG(),
        DbMultiverseSV_SE(),
        DbMultiverseGR_GR(),
        DbMultiverseES_CO(),
        DbMultiverseAR_JO(),
        DbMultiverseTL_PI(),
        DbMultiverseLA_LA(),
        DbMultiverseDA_DK(),
        DbMultiverseCO_FR(),
        DbMultiverseBR_FR(),
        DbMultiverseXX_VE(),
        DbMultiverseXX_LMO(),
    )
}

class DbMultiverseEN : DbMultiverse("en", "en")
class DbMultiverseFR : DbMultiverse("fr", "fr")
class DbMultiverseJP : DbMultiverse("ja", "jp")
class DbMultiverseCN : DbMultiverse("zh", "cn")
class DbMultiverseES : DbMultiverse("es", "es")
class DbMultiverseIT : DbMultiverse("it", "it")
class DbMultiversePT : DbMultiverse("pt", "pt")
class DbMultiverseDE : DbMultiverse("de", "de")
class DbMultiversePL : DbMultiverse("pl", "pl")
class DbMultiverseNL : DbMultiverse("nl", "nl")
class DbMultiverseFR_PA : DbMultiverse("fr", "fr_PA")
class DbMultiverseTR_TR : DbMultiverse("tr", "tr_TR")
class DbMultiversePT_BR : DbMultiverse("pt-BR", "pt_BR")
class DbMultiverseHU_HU : DbMultiverse("hu", "hu_HU")
class DbMultiverseGA_ES : DbMultiverse("ga", "ga_ES")
class DbMultiverseCT_CT : DbMultiverse("ca", "ct_CT")
class DbMultiverseNO_NO : DbMultiverse("no", "no_NO")
class DbMultiverseRU_RU : DbMultiverse("ru", "ru_RU")
class DbMultiverseRO_RO : DbMultiverse("ro", "ro_RO")
class DbMultiverseEU_EH : DbMultiverse("eu", "eu_EH")
class DbMultiverseLT_LT : DbMultiverse("lt", "lt_LT")
class DbMultiverseHR_HR : DbMultiverse("hr", "hr_HR")
class DbMultiverseKR_KR : DbMultiverse("ko", "kr_KR")
class DbMultiverseFI_FI : DbMultiverse("fi", "fi_FI")
class DbMultiverseHE_HE : DbMultiverse("he", "he_HE")
class DbMultiverseBG_BG : DbMultiverse("bg", "bg_BG")
class DbMultiverseSV_SE : DbMultiverse("sv", "sv_SE")
class DbMultiverseGR_GR : DbMultiverse("el", "gr_GR")
class DbMultiverseES_CO : DbMultiverse("es-419", "es_CO")
class DbMultiverseAR_JO : DbMultiverse("ar", "ar_JO")
class DbMultiverseTL_PI : DbMultiverse("fil", "tl_PI")
class DbMultiverseLA_LA : DbMultiverse("la", "la_LA")
class DbMultiverseDA_DK : DbMultiverse("da", "da_DK")
class DbMultiverseCO_FR : DbMultiverse("co", "co_FR")
class DbMultiverseBR_FR : DbMultiverse("br", "br_FR")
class DbMultiverseXX_VE : DbMultiverse("vec", "xx_VE")
class DbMultiverseXX_LMO : DbMultiverse("lmo", "xx_LMO")
