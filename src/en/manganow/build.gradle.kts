import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MangaNow"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "mangareader"

    source {
        lang = "en"
        baseUrl = "https://manganow.to"
    }
}
