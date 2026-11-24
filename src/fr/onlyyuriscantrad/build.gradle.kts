import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Only Yuri Scan Trad"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "oyst"

    source {
        lang = "fr"
        baseUrl = "https://only-yuri-scan-trad.fr"
    }

    deeplink {
        path("/20..*")
    }
}
