import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blue Solo"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "pizzareader"

    source {
        lang = "fr"
        baseUrl = "https://bluesolo.org"
    }
}
