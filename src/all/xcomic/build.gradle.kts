import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XCOMIC"
    versionCode = 6
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "all", "en", "fr", "es", "es-419", "pt", "pt-BR", "ja", "ko", "zh", "ru", "id",
        "ab", "af", "sq", "am", "ar", "hy", "az", "be", "bn", "bs", "bg", "my", "km",
        "ca", "ceb", "hr", "cs", "cv", "da", "nl", "et", "eo", "eu", "fo", "fil", "fi",
        "ka", "de", "el", "gn", "gu", "ht", "ha", "he", "hi", "hu", "is", "ig", "ga",
        "gl", "it", "jv", "kn", "kk", "ku", "ky", "la", "lo", "lv", "lt", "lb", "mk",
        "mg", "ms", "ml", "mt", "mi", "mr", "mo", "mn", "ne", "no", "ny", "ps", "fa",
        "pl", "ro", "rm", "sm", "sr", "sh", "ss", "st", "sn", "sd", "si", "sk", "sl",
        "so", "sw", "sv", "tg", "ta", "te", "th", "ti", "to", "tr", "tk", "uk", "ur",
        "uz", "vi", "yo", "zu", "other",
    ).forEach {
        source {
            lang = it
            baseUrl {
                mirrors(
                    "https://xcomic.me",
                    "https://xcomic.net",
                    "https://comik.to",
                    "https://yona.to",
                )
            }
        }
    }

    deeplink {
        host("xcomic.me")
        host("xcomic.net")
        host("comik.to")
        host("yona.to")
        path("/comic/..*")
    }
}
