import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaDot"
    versionCode = 21
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    pkgName = "en.mangadotnet"

    val oldIds = listOf(
        "ar" to 5133570518916566066L,
        "bn" to 4728703871864086205L,
        "bg" to 4621039982977056475L,
        "my" to 8689086897953658974L,
        "zh" to 4593442970144109426L,
        "zh-Hant" to 2076066796458496830L,
        "cs" to 182506561627032263L,
        "da" to 522919629093846860L,
        "nl" to 5339181991315919474L,
        "en" to 5900936305360403385L,
        "tl" to 5536176722691621839L,
        "fi" to 7568879765052968178L,
        "fr" to 6544312035114371248L,
        "ka" to 7781185259229560796L,
        "de" to 1739134904773959471L,
        "el" to 6280808899001059050L,
        "he" to 7524478288761759786L,
        "hi" to 4686432307246610016L,
        "hu" to 2744567066632059507L,
        "id" to 8591108444263884327L,
        "it" to 8788147393700258423L,
        "ja" to 2305771977147956314L,
        "ko" to 8733946525904795862L,
        "la" to 5980076819966447323L,
        "lt" to 3456620422576095825L,
        "ms" to 2379671138411944871L,
        "mn" to 9192319104809604483L,
        "no" to 2111970709663576933L,
        "fa" to 6840185760082019759L,
        "pl" to 516446519459282312L,
        "pt" to 1374245104599191336L,
        "pt-BR" to 6883842335519142390L,
        "ro" to 7223291528565862680L,
        "ru" to 8911989140118399619L,
        "es" to 1356109540530417190L,
        "es-419" to 5046796980408019790L,
        "sv" to 4958143963089747877L,
        "th" to 8348427309728988846L,
        "tr" to 5964430973280552505L,
        "uk" to 3578850460057110410L,
        "vi" to 3741155905873931805L,
    )

    oldIds.forEach { (langCode, oldId) ->
        source {
            lang = langCode
            baseUrl = "https://mangadot.net"
            id = oldId
        }
    }

    val newLangs = listOf(
        "zu", "yo", "uz", "ur", "tk", "to", "ti", "te", "ta", "tg", "ss", "sw",
        "so", "sl", "sk", "si", "sd", "sn", "st", "sh", "sr", "sm", "rm",
        "ps", "ny", "ne", "mo", "mr", "mi", "mt", "ml", "mg", "mk", "lb", "lv", "lo",
        "ky", "ku", "kk", "kn", "jv", "ga", "ig", "is", "ha", "ht", "gu", "gn", "gl",
        "fo", "et", "eo", "hr", "cv", "ceb", "ca", "km", "bs", "be", "eu", "az", "hy",
        "am", "sq", "af", "ab",
    )

    newLangs.forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://mangadot.net"
        }
    }

    deeplink {
        host("mangadot.net")
        path("/manga/..*")
        path("/chapter/..*")
        path("/volume/..*")
    }
}
