import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XCOMIC"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    listOf(
        "all", "en", "fr", "es", "es-419", "pt", "pt-BR", "ja", "ko", "zh", "zh-Hant", "ru", "id",
        "af", "sq", "ar", "az", "be", "bn", "bg", "my", "km",
        "ca", "ceb", "hr", "cs", "cv", "da", "nl", "et", "eo", "eu", "fil", "fi",
        "ka", "de", "el", "gu", "he", "hi", "hu", "is", "ga",
        "gl", "it", "jv", "kk", "la", "lv", "lt",
        "ms", "ml", "mr", "mo", "mn", "ne", "no", "fa",
        "pl", "ro", "sr", "sh", "ss", "si", "sk", "sl",
        "sv", "ta", "te", "th", "tr", "uk", "ur",
        "uz", "vi", "zu", "other",
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
        path("/title/..*")
        path("/source/..*")
    }
}
