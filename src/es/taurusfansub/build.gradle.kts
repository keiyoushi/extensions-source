import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Taurus Fansub"
    versionCode = 9
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "es"
        baseUrl = "https://lectortaurus.com"
    }
}
