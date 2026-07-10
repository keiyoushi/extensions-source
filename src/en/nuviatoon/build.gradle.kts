plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Nuvia Toon"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://nuviatoon.com"
    }

    deeplink {
        path("/series/..*")
    }
}
