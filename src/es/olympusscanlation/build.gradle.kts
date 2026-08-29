import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Olympus Scanlation"
    versionCode = 21
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl {
            custom("https://olympusxyz.com")
        }
        versionId = 3
    }
}
