package eu.kanade.tachiyomi.extension.all.comickfun

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

// A legacy mapping  of language codes to ensure that source IDs don't change
val legacyLanguageMappings = mapOf(
    "pt-br" to "pt-BR", // Brazilian Portuguese
    "zh-hk" to "zh-Hant", // Traditional Chinese,
    "zh" to "zh-Hans", // Simplified Chinese
).withDefault { it } // country code matches language code

class ComickFunFactory : SourceFactory {
    private val idMap = listOf(
        "all" to 982606170401027267,
        "en" to 2971557565147974499,
        "pt-br" to 8729626158695297897,
        "ru" to 5846182885417171581,
        "fr" to 9126078936214680667,
        "es-419" to 3182432228546767958,
        "pl" to 7005108854993254607,
        "tr" to 7186425300860782365,
        "it" to 8807318985460553537,
        "es" to 9052019484488287695,
        "id" to 5506707690027487154,
        "hu" to 7838940669485160901,
        "vi" to 9191587139933034493,
        "zh-hk" to 3140511316190656180,
        "ar" to 8266599095155001097,
        "de" to 7552236568334706863,
        "zh" to 1071494508319622063,
        "ca" to 2159382907508433047,
        "bg" to 8981320463367739957,
        "th" to 4246541831082737053,
        "fa" to 3146252372540608964,
        "uk" to 3505068018066717349,
        "mn" to 2147260678391898600,
        "ro" to 6676949771764486043,
        "he" to 5354540502202034685,
        "ms" to 4731643595200952045,
        "tl" to 8549617092958820123,
        "ja" to 8288710818308434509,
        "hi" to 5176570178081213805,
        "my" to 9199495862098963317,
        "ko" to 3493720175703105662,
        "cs" to 2651978322082769022,
        "pt" to 4153491877797434408,
        "nl" to 6104206360977276112,
        "sv" to 979314012722687145,
        "bn" to 3598159956413889411,
        "no" to 5932005504194733317,
        "lt" to 1792260331167396074,
        "el" to 6190162673651111756,
        "sr" to 571668187470919545,
        "da" to 7137437402245830147,
    ).toMap()
    override fun createSources(): List<Source> = idMap.keys.map {
        object : ComickFun(legacyLanguageMappings.getValue(it), it) {
            override val id: Long = idMap[it]!!
        }
    }
}
